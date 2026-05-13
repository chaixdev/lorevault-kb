# Catalog Module

**Status:** Design — redeveloping from scratch as `lorevault-catalog` Maven submodule
**Last Updated:** May 14, 2026
**Depends on:** Phase 0 relation claim extraction, `RelationClaim` model

## Summary

The catalog is a **reverse dictionary** — a managed vocabulary of relation kinds. It answers one question: *"Given this description of a relationship I found in the text (its name, the entity kinds involved, what the LLM says it means), what key should I use to refer to it?"*

The catalog is a **matching engine**, not a storage system with exact-match lookups. The consumer (ingestion pipeline) provides a rich query — raw name, entity kinds, description, certainty, evidence context. The catalog finds the best matching definition, disambiguates when needed, and returns a single stable identity. If nothing matches, it admits it doesn't know this one yet and creates a new definition.

The catalog owns the **definition** of relation kinds — their stable identity, display name, entity kind signatures, description, and (in future) semantic embedding. The graph owns the **provenance** — who said what about whom, with what evidence. The graph stores a `catalogId` reference. Whether that definition represents a single term or a cluster of related terms is the catalog's internal business — the graph is insulated from the difference.

## Problem

The graph has entity and event nodes but almost no typed edges between them:
- `Scene -[:CONTAINS]-> EntityMention`
- `EntityMention -[:REFERS_TO]-> ChapterEntity -[:REFERS_TO]-> BookEntity`

Questions like "Who is a member of this crew?" or "Which organizations operate at this location?" cannot be answered directly. Letting the LLM create arbitrary relationship labels creates a synonym explosion. Forcing a predefined taxonomy loses useful detail. The catalog accumulates relation kinds at runtime, providing stable identities the graph can reference.

## Matching Strategy

Three-tier matching, applied in order by `resolve()`:

```
Consumer provides: { rawName, subjectKind, objectKind, description, certainty, evidence }

    │
    ▼
1. NORMALIZE rawName → definitionKey (lowercase, trim, underscores)
    │
    ▼
2. EXACT MATCH: SELECT definition WHERE definition_key = ?
    ├── Found → update last_seen, return definition (reuse stable identity)
    └── Not found → continue
    │
    ▼
3. SIGNATURE MATCH: SELECT definition FROM signature WHERE (subjectKind, objectKind) match
    ├── Found → update last_seen, return definition (disambiguation)
    └── Not found → continue
    │
    ▼
4. CREATE NEW: INSERT definition + signature + variant, return new definition
```

Tier 3 is the disambiguation: "I've seen Individual→Collective relations before — maybe 'leads' and 'commands' are the same thing." The signature acts as a fast filtering pass. In a future iteration, embedding-based semantic similarity (pgvector) will augment or replace the signature match.

Tier 4 admits the catalog genuinely doesn't know this relation kind yet. A new definition is created with the LLM's description as its canonical description, ready for future matching.

## Expected Structure

The catalog is a **Maven submodule** (`lorevault-catalog`) — not a sub-package of `lorevault-core`. This provides true domain isolation: the catalog's `pom.xml` declares only its own dependencies (`spring-boot-starter-jdbc`, `flyway-core`, `postgresql`). It has zero knowledge of Neo4j, ingestion, content, or any other LoreVault domain.

```
lorevault-kb/
├── pom.xml                              (+ <module>lorevault-catalog</module>)
├── lorevault-catalog/                   (standalone — no LoreVault dependencies)
│   ├── pom.xml                          (jdbc, flyway, postgresql, testcontainers-postgresql,
│   │                                     spring-modulith-core, lombok)
│   └── src/
│       ├── main/java/com.lorevault.api.catalog/
│       │   ├── RelationCatalogService.java       (public interface)
│       │   ├── RelationCatalogDefinition.java    (record — output)
│       │   ├── RelationCatalogId.java            (record — UUID wrapper)
│       │   ├── RelationQuery.java                (record — input from consumer)
│       │   ├── RelationKindSignature.java        (record — kind pair)
│       │   ├── package-info.java                 (@ApplicationModule(type=CLOSED))
│       │   └── internal/
│       │       ├── PostgresRelationCatalogStore.java
│       │       ├── CatalogConfig.java
│       │       ├── CatalogDataSourceProperties.java
│       │       └── PostgresCatalogSchemaInitializer.java
│       ├── main/resources/db/migration/catalog/
│       │   └── V1__catalog_definition.sql
│       └── test/java/com.lorevault.api.catalog/
│           ├── RelationCatalogServiceTest.java    (integration tests, Testcontainers)
│           └── CatalogDisabledConfigurationTest.java
├── lorevault-core/                       (+ <dependency>lorevault-catalog</dependency>)
│   └── src/.../
│       └── content/relation/RelationClaim.java    (stores catalogId: UUID)
│       └── ingestion/.../RelationClaimPersistenceService.java  (calls resolve())
└── lorevault-web/                        (gets catalog transitively via core)
    └── src/.../
        ├── web/CatalogController.java             (REST endpoints)
        └── architecture/ModulithVerificationTest.java
```

### Boundary Enforcement (Three Layers)

| Layer | Mechanism | What it enforces |
|-------|-----------|------------------|
| Maven | `lorevault-catalog` has no dependency on `lorevault-core` or `lorevault-web` | Catalog cannot import any LoreVault domain types |
| Java | `internal` package is not exported; public API is the `com.lorevault.api.catalog` package | Only interface + records are accessible |
| Modulith | `@ApplicationModule(type = CLOSED)` on `package-info.java` | Runtime verification that boundaries hold |

### Dependency Direction

- `lorevault-core` depends on `lorevault-catalog` (only the public API).
- `lorevault-web` depends on `lorevault-catalog` transitively via `lorevault-core`.
- `lorevault-catalog` depends on **nothing** in LoreVault. Only Spring Boot, JDBC, Flyway, PostgreSQL.

## Public Contract

```java
public interface RelationCatalogService {

    /**
     * Resolve a relation query to a catalog definition.
     *
     * Three-tier matching: exact match on definitionKey → signature match
     * on (subjectKind, objectKind) → create new definition if nothing matches.
     *
     * @param query the relation to resolve
     * @return the matched (or newly created) catalog definition
     */
    RelationCatalogDefinition resolve(RelationQuery query);

    /**
     * Find a catalog definition by its stable identity.
     */
    Optional<RelationCatalogDefinition> findByKey(RelationCatalogId id);

    /**
     * Find a catalog definition by its exact-match lookup key.
     */
    Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey);
}
```

### RelationQuery

```java
public record RelationQuery(
    String definitionKey,             // normalized: lowercase, trim, underscores
    String rawName,                   // raw relation name as extracted (e.g. "is a member of")
    String subjectKind,
    String objectKind,
    String description,               // from LLM — what this relation means
    String certainty,                 // "Explicit" | "StronglyImplied" | "WeaklyImplied"
    String evidenceReference,         // reference to the source claim
    UUID chapterId,
    UUID sceneId,
    Optional<String> cappedEvidenceSnippet
) {}
```

### RelationCatalogDefinition

```java
public record RelationCatalogDefinition(
    RelationCatalogId id,                   // stable identity — never changes
    String definitionKey,                   // exact-match lookup key: "is_a_member_of"
    String displayName,                     // first raw name seen: "is a member of"
    String description,                     // from LLM — canonical description
    List<RelationKindSignature> signatures, // (subjectKind, objectKind) pairs seen
    List<String> rawNameVariants,          // distinct raw names for this definition
    Instant created,
    Instant updated,
    Instant lastSeen
) {}
```

### RelationKindSignature

```java
public record RelationKindSignature(
    String subjectKind,
    String objectKind
) {}
```

Presence only — no observation count. Counting is the graph's job.

### RelationCatalogId

```java
public record RelationCatalogId(UUID value) {
    public static RelationCatalogId random() { ... }
    public static RelationCatalogId fromString(String uuidString) { ... }
}
```

## Schema

Single Flyway migration. Three tables:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS catalog_definition (
    id              UUID PRIMARY KEY,
    definition_key  TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    description     TEXT,
    created         TIMESTAMPTZ NOT NULL,
    updated         TIMESTAMPTZ NOT NULL,
    last_seen       TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_definition_variant (
    definition_id   UUID NOT NULL REFERENCES catalog_definition(id),
    raw_name        TEXT NOT NULL,
    PRIMARY KEY (definition_id, raw_name)
);

CREATE TABLE IF NOT EXISTS catalog_definition_signature (
    definition_id   UUID NOT NULL REFERENCES catalog_definition(id),
    subject_kind    TEXT NOT NULL,
    object_kind     TEXT NOT NULL,
    PRIMARY KEY (definition_id, subject_kind, object_kind)
);

CREATE INDEX IF NOT EXISTS idx_signature_kinds
    ON catalog_definition_signature(subject_kind, object_kind);
```

For future embedding matching:

```sql
ALTER TABLE catalog_definition ADD COLUMN embedding vector(1536);
CREATE INDEX ON catalog_definition USING ivfflat (embedding vector_cosine_ops);
```

## Integration Points

### Ingestion

The catalog is consulted **before** graph projection via `resolve()`:

```
LLM extracts relation claims from scene analysis

For each claim:
    1. Build RelationQuery from extraction:
       ├── normalize rawName → definitionKey
       ├── subjectKind, objectKind
       ├── description (from LLM)
       ├── certainty, evidence snippet
    2. Call catalog.resolve(query)
    3. Store definitionId as catalogId on RelationClaim
    4. Persist RelationClaim to Neo4j
```

If the catalog is disabled (`lorevault.catalog.enabled=false`) or unavailable (`DataAccessException`), the pipeline persists `RelationClaim` with `catalogId=null` and continues. The catalog is an enhancement, not a hard dependency.

### Graph

`RelationClaim` stores only `catalogId` (UUID). No `definitionKey`, no `normalizedRelationKey`, no `resolutionStatus`. The graph queries the catalog for human-readable names. The `definitionKey` is a catalog lookup concern, not a graph concern.

### REST API

```
GET /api/catalog/definitions/{id}                  → findByKey
GET /api/catalog/definitions?definitionKey={key}    → findByDefinitionKey
```

## Progression

| Step | Capability | Status |
|------|-----------|--------|
| 1 | `lorevault-catalog` Maven submodule with public API + schema | 🔲 Implement |
| 2 | `resolve()` with exact-match + signature matching | 🔲 Implement |
| 3 | Integration with ingestion pipeline via `RelationQuery` | 🔲 Implement |
| 4 | REST endpoints for definition lookup | 🔲 Implement |
| 5 | Boundary enforcement (Maven, Java, Modulith) verified | 🔲 Implement |
| 6 | Embedding-based semantic similarity matching (pgvector) | 🔲 Planned |
| 7 | Auto-classification and stable graph-edge projection | 🔲 Planned |

## Out of Scope

- Embedding generation and vector similarity matching (step 6).
- LLM calls inside the catalog module.
- Human review, promotion, or lifecycle status management.
- Stable graph-edge projection.
- Inverse relation modeling.
- Generic catalog abstractions for ascriptions, properties, or actions.

## Open Questions

1. How should `description` be populated on definition creation? **Candidate:** Use the LLM's `relationDescription` from the first claim that triggers creation.
2. What should `RelationQuery.description` be when the LLM provides no description? **Candidate:** `null` — the definition's `description` remains null until a claim with a description triggers creation.
3. When signature matching produces multiple results (same kind pair, different `definitionKey`s), which definition wins? **Candidate:** First match (arbitrary). Coarse disambiguation at this stage — embedding matching will replace or refine this logic later.
4. Should `RelationClaim` store `definitionKey` on the node for graph-native queries? **Candidate:** No. `catalogId` only. The graph queries the catalog for human-readable names.

## Success Criteria

- [ ] `lorevault-catalog` Maven submodule created with own `pom.xml`, no dependency on `lorevault-core` or `lorevault-web`.
- [ ] `com.lorevault.api.catalog` package with public API types: `RelationCatalogService`, `RelationCatalogDefinition`, `RelationCatalogId`, `RelationQuery`, `RelationKindSignature`.
- [ ] `@ApplicationModule(type = CLOSED)` on `package-info.java`.
- [ ] `resolve()` implements three-tier matching: exact match → signature match → create new.
- [ ] Database schema: `catalog_definition` + `catalog_definition_variant` + `catalog_definition_signature`. No observation table. No observation counts. No status columns.
- [ ] `RelationClaim` stores only `catalogId` (UUID). No `definitionKey`, no `normalizedRelationKey`, no `resolutionStatus`.
- [ ] `RelationClaimPersistenceService` builds `RelationQuery` from extraction, calls `catalogService.resolve()`.
- [ ] `CatalogController` exposes `GET /api/catalog/definitions/{id}` and `GET /api/catalog/definitions?definitionKey={key}`.
- [ ] Integration tests with Testcontainers PostgreSQL verify idempotency and matching logic.
- [ ] Spring Modulith verification passes.
- [ ] ArchUnit boundary rules pass.
- [ ] Planning doc reflects clean-slate design (no M1 history, no migration notes).

## Links

- `docs/planning/relation-evidence-harvesting.md` — relation evidence harvesting design and extraction context
- `docs/planning/qa-retrieval-quality-validation.md` — relation questions that should guide catalog usefulness
- `docs/planning/concept-resolution-lane.md` — Concept entity lane, needed for Concept-targeting relation signatures
- `docs/concepts/Entity-Event-Claim-model.md` — current entity/event/claim model
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — broader graph process context
- `docs/patterns/ingestion/triad-analysis.md` — triad normalization pipeline that will consume catalog outputs
- `docs/brainstorm/architecture/2026-05-11_orchestration-domain-separation.md` — catalog as first closed internal module
