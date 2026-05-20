# Catalog Module

**Status:** M0 + M1 Implemented — M0 (contract & interface) and M1 (PostgreSQL + exact match) complete
**Last Updated:** May 14, 2026 (implemented M0 + M1, updated deviations and implementation notes)
**Depends on:** Phase 0 relation claim extraction, `RelationClaim` model

## Summary

The catalog is a **reverse dictionary** — a managed vocabulary of relation kinds. It answers one question: *"Given this description of a relationship I found in the text (its name, the entity kinds involved, what the LLM says it means), what key should I use to refer to it?"*

The catalog is a **matching engine**, not a storage system with exact-match lookups. The consumer (ingestion pipeline) provides a rich query — raw name, entity kinds, description, certainty, evidence context. The catalog finds the best matching definition, disambiguates when needed, and returns a single stable identity. If nothing matches, it admits it doesn't know this one yet and creates a new definition.

The catalog owns the **definition** of relation kinds — their stable identity, display name, entity kind signatures, description, and (in future) semantic embedding. The graph owns the **provenance** — who said what about whom, with what evidence. The graph stores `catalogId` and `definitionKey` references. Whether that definition represents a single term or a cluster of related terms is the catalog's internal business — the graph is insulated from the difference.

## Problem

The graph has entity and event nodes but almost no typed edges between them:
- `Scene -[:CONTAINS]-> EntityMention`
- `EntityMention -[:REFERS_TO]-> ChapterEntity -[:REFERS_TO]-> BookEntity`

Questions like "Who is a member of this crew?" or "Which organizations operate at this location?" cannot be answered directly. Letting the LLM create arbitrary relationship labels creates a synonym explosion. Forcing a predefined taxonomy loses useful detail. The catalog accumulates relation kinds at runtime, providing stable identities the graph can reference.

## Matching Strategy

Two-tier matching for MVP, applied in order by `resolve()`:

```
Consumer provides: { rawName, subjectKind, objectKind, description, certainty, evidence }

    │
    ▼
1. NORMALIZE rawName → definitionKey (namespaced: `R:` prefix + lowercase + underscores, e.g. "is a member of" → `R:is_a_member_of`)
    │
    ▼
2. EXACT MATCH: SELECT definition WHERE definition_key = ?
    ├── Found → update last_seen, return definition (reuse stable identity)
    └── Not found → continue
    │
    ▼
3. CREATE NEW: INSERT definition + signature + variant, return new definition
```

**Why no signature match in MVP.** The `(subjectKind, objectKind)` signature is too coarse for disambiguation. An `Individual→Individual` signature would match "leads", "betrayed", "is married to", "trained under" — semantically unrelated relations. Assigning them the same `catalogId` creates false equivalence that downstream consumers (graph projection, Q&A retrieval) will trust. This is worse than having no disambiguation at all.

The safer strategy is **create many, merge later**: let each distinct `definitionKey` produce its own definition, then use embedding-based semantic similarity (pgvector, planned for a future milestone) to cluster genuinely related terms. This is reversible — you can merge definitions that shouldn't be separate. False merges are much harder to detect and undo.

Tier 3 (create new) admits the catalog genuinely doesn't know this relation kind yet. A new definition is created with the LLM's description as its canonical description, ready for future matching.

## Expected Structure

The catalog is a **Maven submodule** (`lorevault-catalog`) — not a sub-package of `lorevault-core`. This provides true domain isolation: the catalog's `pom.xml` declares only its own dependencies (`spring-boot-starter-jdbc`, `flyway-core`, `postgresql`). It has zero knowledge of Neo4j, ingestion, content, or any other LoreVault domain.

**Package convention.** The catalog module uses `com.lorevault.catalog` (not `com.lorevault.api.catalog`). The existing codebase uses `com.lorevault.api.*` as a legacy base package — the `api` segment is a leftover from when the project was a single `lorevault-api` module. New modules should use the cleaner `com.lorevault.{domain}` convention. A future task will rename the existing `com.lorevault.api.*` packages to drop the `api` segment. Spring component scanning must cover both `com.lorevault.api` and `com.lorevault.catalog` until the rename is complete.

```
lorevault-kb/
├── pom.xml                              (+ <module>lorevault-catalog</module>)
├── lorevault-catalog/                   (standalone — no LoreVault dependencies)
│   ├── pom.xml                          (jdbc, flyway, postgresql, testcontainers-postgresql,
│   │                                     spring-modulith-core, lombok)
│   └── src/
│       ├── main/java/com.lorevault.catalog/
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
│       └── test/java/com.lorevault.catalog/
│           ├── RelationCatalogServiceTest.java    (integration tests, Testcontainers)
│           └── CatalogDisabledConfigurationTest.java
├── lorevault-core/                       (+ <dependency>lorevault-catalog</dependency>)
│   └── src/.../
│       └── content/relation/RelationClaim.java    (stores catalogId + definitionKey; removes provisionalRelTypeId + resolutionStatus)
│       └── ingestion/.../RelationClaimPersistenceService.java  (calls resolve())
└── lorevault-web/                        (gets catalog transitively via core)
    └── src/.../
        └── architecture/ModulithVerificationTest.java
```

### Boundary Enforcement (Three Layers)

| Layer | Mechanism | What it enforces |
|-------|-----------|------------------|
| Maven | `lorevault-catalog` has no dependency on `lorevault-core` or `lorevault-web` | Catalog cannot import any LoreVault domain types |
| Java | `internal` package is not exported; public API is the `com.lorevault.catalog` package | Only interface + records are accessible |
| Modulith | `@ApplicationModule(type = CLOSED)` on `package-info.java` | Runtime verification that boundaries hold |

### Dependency Direction

- `lorevault-core` depends on `lorevault-catalog` (only the public API).
- `lorevault-web` depends on `lorevault-catalog` transitively via `lorevault-core`.
- `lorevault-catalog` depends on **nothing** in LoreVault. Only Spring Boot, JDBC, Flyway, PostgreSQL.

## Transactionality

The catalog's modular boundary implies a **transactional boundary**. Each module owns its database transactions:

- **Catalog module** owns PostgreSQL transactions. Its `resolve()` method runs in its own transaction against the catalog database.
- **Core/web modules** own Neo4j transactions. The existing `@Transactional` on `persistExtractedRelationClaims()` is a Neo4j transaction — it knows nothing about PostgreSQL.

**Never nest one inside the other.** A Spring Data Neo4j `@Transactional` does not cover JDBC writes to PostgreSQL, and vice versa. Wrapping a catalog call inside a Neo4j transaction gives a false sense of atomicity.

### `resolve()` is an atomic side-effecting query

`resolve()` is fundamentally a **query that sometimes materializes**. The caller cannot know in advance whether it will be a read (cache hit) or a write (miss + INSERT). This has two consequences:

1. **The catalog must own its own transaction boundary regardless of read vs write.** The caller should never wrap a catalog call in its own transaction.

```java
// In RelationCatalogService implementation
@Transactional(propagation = REQUIRES_NEW)  // always own transaction
public RelationCatalogDefinition resolve(RelationQuery query) {
    // Hit: SELECT only — cheap read-only tx
    // Miss: SELECT + INSERT — read-write tx
    // Either way, catalog owns the boundary
}
```

Using `REQUIRES_NEW` ensures the catalog's transaction commits or rolls back independently of whatever Neo4j transaction the caller is in. No false nesting, no half-committed states.

2. **The caller treats `resolve()` as an atomic `getOrCreate`.** The ingestion pipeline's contract is: "give me the stable identity for this relation kind." Whether that involved a cache hit, a DB read, or a new row insertion is the catalog's business. The pipeline gets back a `catalogId` (or null in degradation mode) and proceeds with its Neo4j write.

### Ingestion pipeline pattern

```
❌  @Transactional  // Neo4j transaction
    persistExtractedRelationClaims() {
        catalogId = catalogService.resolve(query)  // ← PostgreSQL write inside Neo4j tx
        claim.setCatalogId(catalogId)
        neo4jRepository.save(claim)                 // ← Neo4j write
    }
    // If Neo4j save fails, PostgreSQL write is NOT rolled back

✅  // Step 1: Catalog resolves in its own transaction (PostgreSQL)
    catalogId = catalogService.resolve(query)       // ← separate PostgreSQL tx (REQUIRES_NEW)

    // Step 2: Neo4j persistence with resolved catalogId
    @Transactional  // Neo4j transaction only
    persistExtractedRelationClaims(catalogId) {
        claim.setCatalogId(catalogId)
        neo4jRepository.save(claim)                 // ← Neo4j write
    }
    // If Neo4j save fails, catalog has a phantom definition — but that's idempotent
```

### Idempotency

`resolve()` must be idempotent on the miss path. If two concurrent ingestion calls both miss on "is allied with," both will try to INSERT. Exactly one should win — `ON CONFLICT DO NOTHING` on the unique `(definition_key)` constraint handles this. Phantom definitions from partial failures (catalog INSERT succeeded, Neo4j write failed) are harmless: they're unreferenced rows that a future cleanup job can sweep. **This is an M1 requirement, not optional hardening.**

## Embedding Integration (M3)

The catalog needs embedding calls for semantic matching (M3), but must not depend on `lorevault-core` or Spring AI. The solution is a **function interface owned by the catalog, implemented by core**:

```java
// In lorevault-catalog — catalog owns the contract
package com.lorevault.catalog;

@FunctionalInterface
public interface EmbeddingFunction {
    float[] embed(String text);
}
```

```java
// In lorevault-core — core provides the implementation
@Configuration
public class CatalogEmbeddingConfig {

    @Bean
    @ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
    public EmbeddingFunction embeddingFunction(
            @Qualifier("embeddingModel") EmbeddingModel embeddingModel) {
        return embeddingModel::embed;
    }
}
```

```java
// In catalog's internal store — zero Spring AI knowledge
public class PostgresRelationCatalogStore {
    private final EmbeddingFunction embeddingFunction;  // injected

    public Optional<RelationCatalogDefinition> findBestMatch(String description) {
        float[] queryVector = embeddingFunction.embed(description);
        // pgvector similarity search...
    }
}
```

### Why not a shared `lorevault-ai` module?

A shared module pulling AI infrastructure out of `lorevault-core` was considered and rejected for MVP:

1. **The catalog's AI need is narrow.** M3 needs exactly one operation: `embed(String) → float[]`. ~90% of the current AI infrastructure (ChatClients, prompt templates, retry policies, health checks) is irrelevant to the catalog.
2. **The function interface is a stronger boundary.** The catalog has zero knowledge of Spring AI — it can't accidentally import `EmbeddingModel` or any Spring AI types. A shared module would expose the full Spring AI surface to the catalog.
3. **`SpringAiConfig` can't be cleanly split.** The 4 beans share private helpers (`restClientBuilderWithTimeout`, `buildApi`) and properties that span a single YAML prefix (`lorevault.ai.models.*`). Splitting would create configuration drift or duplicate sources of truth.
4. **No dumping ground risk.** A shared module inevitably attracts "AI-adjacent" code that doesn't belong together. The function interface prevents this — the catalog can't shove random things into an interface it owns.
5. **Zero operational weight.** No new POM, no reactor order dependency, no CI surface. The interface is 3 lines of Java.

**When would extraction make sense?** If a third module (`lorevault-agent`, `lorevault-eval`) genuinely needs the full AI stack (ChatClient + EmbeddingModel + retry + prompts), then extract `lorevault-ai`. At that point there are two real consumers and the boundary is informed by actual usage. The function interface doesn't preclude this — the catalog just changes its field type from `EmbeddingFunction` to `EmbeddingModel`.

### Batch embedding

If the catalog eventually needs batch embedding for efficiency, the interface extends with a default method:

```java
@FunctionalInterface
public interface EmbeddingFunction {
    float[] embed(String text);

    // Default: naive loop; core provides optimized override
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
```

Core's `CatalogEmbeddingConfig` overrides `embedBatch` using `EmbeddingModel.call(EmbeddingRequest)` for batch efficiency. The catalog never imports Spring AI types.

## Public Contract

```java
public interface RelationCatalogService {

    /**
     * Resolve a relation query to a catalog definition.
     *
     * Two-tier matching: exact match on definitionKey → create new definition
     * if nothing matches. Signature-based disambiguation is deferred to a
     * future milestone (embedding similarity via pgvector).
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
    String definitionKey,             // namespaced normalized key: "R:is_a_member_of"
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
    String definitionKey,                   // namespaced lookup key: "R:is_a_member_of"
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

-- Index deferred to M3: signature matching is not used until embedding similarity is available.
-- CREATE INDEX IF NOT EXISTS idx_signature_kinds
--     ON catalog_definition_signature(subject_kind, object_kind);
```

**pgvector deferred.** The `CREATE EXTENSION IF NOT EXISTS vector` and embedding column are not in V1. The standard `postgres:16` Docker image does not include pgvector — it requires `pgvector/pgvector:pg16`. Deferring the extension avoids a deployment dependency that isn't needed until embedding matching (a future milestone). When embedding matching is implemented, a V2 migration will add:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
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
       ├── normalize rawName → definitionKey (R: prefix + lowercase + underscores)
       ├── subjectKind, objectKind
       ├── description (from LLM)
       ├── certainty, evidence snippet
    2. Call catalog.resolve(query)          ← separate PostgreSQL tx (REQUIRES_NEW)
    3. Store catalogId + definitionKey on RelationClaim
    4. Persist RelationClaim to Neo4j        ← Neo4j tx only
```

If the catalog is disabled (`lorevault.catalog.enabled=false`) or unavailable (`DataAccessException`), the pipeline persists `RelationClaim` with `catalogId=null` and continues. The catalog is an enhancement, not a hard dependency.

### Graph

`RelationClaim` stores both `catalogId` (UUID) and `definitionKey` (string). The `catalogId` is the stable identity for programmatic reference and foreign key lookups. The `definitionKey` provides human-readable context during graph traversal — a Cypher query can show what a relation represents without a catalog round-trip. No `normalizedRelationKey`, no `resolutionStatus`.

### RelationClaim Field Migration

The catalog replaces the ad-hoc `provisionalRelTypeId` with proper catalog identities. The current `RelationClaim` record has fields that shift ownership:

| Action | Field | Reason |
|--------|-------|--------|
| **Remove** | `provisionalRelTypeId` | Replaced by `definitionKey` from catalog. The old `"R:provisional.is_a_member_of"` format becomes `"R:is_a_member_of"` — the `provisional` qualifier is dropped because the catalog assigns stable identities. |
| **Remove** | `resolutionStatus` | Dead field — only ever set to `"unresolved"` on `RelationClaim`, never transitioned. With the catalog, `catalogId != null` is the resolution status. The `resolutionStatus` field on Mention nodes (entity resolution) is unrelated and unaffected. |
| **Add** | `catalogId` (UUID, nullable) | Stable identity from catalog. Null when catalog is disabled or unavailable. |
| **Add** | `definitionKey` (String, nullable) | Namespaced human-readable key (e.g., `R:is_a_member_of`). Null when catalog is disabled or unavailable. |

The two Neo4j indexes on `(chapterId, provisionalRelTypeId)` and `(bookId, provisionalRelTypeId)` must be replaced with indexes on `definitionKey`.

### definitionKey Naming Convention

`definitionKey` values use a namespace prefix to make keys self-describing in the graph and leave room for future catalog types:

- **Relation kinds:** `R:` prefix — e.g., `R:is_a_member_of`, `R:leads`, `R:betrays`
- **Future entity kinds:** `E:` prefix — e.g., `E:soldier`, `E:headquarters` (not in this planning doc)

The normalization function produces `R:` + lowercase + underscores from the raw name: `"is a member of"` → `"R:is_a_member_of"`.

### REST API (Optional)

Not required for MVP. The catalog's primary consumer is the ingestion pipeline calling `catalogService.resolve()` internally. A REST API for browsing definitions can be added later if useful for debugging or admin tooling.

```
GET /api/catalog/definitions/{id}                  → findByKey
GET /api/catalog/definitions?definitionKey={key}    → findByDefinitionKey
```

## Progression

| Milestone | Capability | Effort | Status |
|-----------|-----------|--------|--------|
| **M0: Contract & Interface** | `lorevault-catalog` Maven submodule, public API types, `InMemoryRelationCatalogStore` (exact-match only, no PostgreSQL), `catalogId` + `definitionKey` on `RelationClaim`, `@ApplicationModule(CLOSED)`, ArchUnit rules | S (2-3 days) | ✅ |
| **M1: PostgreSQL + Exact Match** | `PostgresRelationCatalogStore`, Flyway V1 schema, Testcontainers PostgreSQL, `docker-compose.yml` postgres service, DataSource exclusion fix, Flyway `locations` scoping, idempotent `findOrCreate` (`ON CONFLICT DO NOTHING`) | M (1-2 weeks) | ✅ |
| **M2: Hardening** | Metrics, health indicator. ~~Backfill detection~~ (deferred — greenfield, no orphaned claims yet). `CatalogDisabledConfiguration` moved to M0/M1. | M (1-2 weeks) | 🔲 |
| **M3: Embedding Matching** | `EmbeddingFunction` interface (catalog owns contract, core provides impl), pgvector extension, embedding generation for `description`, similarity threshold, replace exact-only with semantic matching, `pgvector/pgvector:pg16` Docker image | L (2-4 weeks) | 🔲 Planned |
| **M4: Graph Edge Projection** | `REL` edges in Neo4j from resolved `catalogId`, graph-aware retrieval, Q&A validation | L (3-5 weeks) | 🔲 Planned |

## Implementation Risks

| Risk | Severity | Mitigation |
|------|----------|-------------|
| `LoreVaultApiApplication` excludes `DataSourceAutoConfiguration` | **High** | The app currently has no JDBC DataSource. Adding `lorevault-catalog` (which brings `spring-boot-starter-jdbc`, `flyway-core`, `postgresql`) to the classpath will silently fail — Spring won't create a `DataSource`. Must remove or scope the exclusion before M1. |
| Flyway `locations` not scoped | **Medium** | Once DataSource auto-config is re-enabled, Flyway will scan the entire classpath for migrations. Must set `spring.flyway.locations=classpath:db/migration/catalog` explicitly to avoid picking up phantom migration directories. |
| Dual-database transaction inconsistency | **Medium** | Catalog writes to PostgreSQL, core writes to Neo4j — separate transactions. If one succeeds and the other fails, data is inconsistent. Mitigated by: (1) catalog `resolve()` uses `REQUIRES_NEW` so it commits independently, (2) `ON CONFLICT DO NOTHING` makes catalog writes idempotent, (3) `catalogId=null` degradation mode means the pipeline never blocks on catalog failure. |
| Claims ingested during catalog downtime are permanently orphaned | **Medium** | `catalogId=null` claims have no backfill mechanism. Document as known limitation; plan a backfill job that re-processes unresolved claims. |
| PostgreSQL not in `docker-compose.yml` or CI | **Medium** | Must add `postgres` service to `docker-compose.yml` and Testcontainers PostgreSQL to CI. Use standard `postgres:16` for MVP (no pgvector needed until M3). |
| pgvector extension not in standard `postgres:16` image | **Low** | Deferred to M3. When needed, switch to `pgvector/pgvector:pg16` Docker image. |
| Empty `com.lorevault.api.catalog` package in `lorevault-core` | **Low** | Currently exists as a placeholder. Must be deleted to avoid confusion about which module owns the catalog package. ArchUnit won't catch this since it already allows `catalog`. |
| `RelationClaim` record has 17 fields, adding 2 more (catalogId, definitionKey) while removing 2 (provisionalRelTypeId, resolutionStatus) | **Low** | Net zero change in field count. Consider a builder pattern for `RelationClaim` to make future field additions cheaper. |

## Out of Scope

- Embedding generation and vector similarity matching (step 6) — planned for M3 via the `EmbeddingFunction` interface pattern (see [Embedding Integration](#embedding-integration-m3)).
- LLM calls inside the catalog module. The catalog is a matching engine, not an LLM orchestrator. It receives pre-extracted data from the ingestion pipeline.
- Shared `lorevault-ai` Maven module. The catalog's embedding need is served by the `EmbeddingFunction` interface pattern — catalog owns the contract, core provides the implementation. A shared module would be premature (see [Embedding Integration](#embedding-integration-m3)).
- Human review, promotion, or lifecycle status management.
- Stable graph-edge projection.
- Inverse relation modeling.
- Generic catalog abstractions for ascriptions, properties, or actions.
- Entity kind catalog (e.g., `E:soldier`, `E:headquarters`). Entity kinds have multi-dimensional clustering (gender, profession, social status) that doesn't fit the flat normalize→match pattern. Disambiguation of entity kind properties is a catalog concern, but the data model is fundamentally different and requires separate design.
- REST API (Optional). Not required for MVP. The catalog's primary consumer is the ingestion pipeline calling `catalogService.resolve()` internally. A REST API for browsing definitions can be added later if useful for debugging or admin tooling.

## Resolved Questions

1. **How should `description` be populated on definition creation?** Use the LLM's `relationDescription` from the first claim that triggers creation. Subsequent claims with different descriptions do not update it — the description is advisory display text, not matching input.

2. **What should `RelationQuery.description` be when the LLM provides no description?** `null`. The definition's `description` remains null until a claim with a description triggers creation.

3. **When signature matching produces multiple results, which definition wins?** Signature match is **skipped in MVP**. The `(subjectKind, objectKind)` signature is too coarse — it would conflate semantically unrelated relations. Exact-match-only until embedding similarity is available. See [Matching Strategy](#matching-strategy).

4. **Should `RelationClaim` store `definitionKey` on the node?** Yes. Both `catalogId` (UUID) and `definitionKey` (string) are stored on `RelationClaim`. The UUID is for programmatic reference; the `definitionKey` provides human-readable context during graph traversal without a catalog round-trip.

5. **Should AI infrastructure be extracted into a shared `lorevault-ai` module?** No. The catalog's embedding need is served by the `EmbeddingFunction` interface pattern — the catalog defines the contract (`float[] embed(String text)`), core provides the implementation (wrapping Spring AI's `EmbeddingModel`). This is a stronger boundary (catalog has zero Spring AI knowledge), has no operational weight (no new POM), and avoids the shared-module dumping-ground risk. Extraction into `lorevault-ai` is justified only when a third module genuinely needs the full AI stack.

## Success Criteria

- [x] `lorevault-catalog` Maven submodule created with own `pom.xml`, no dependency on `lorevault-core` or `lorevault-web`.
- [x] `com.lorevault.catalog` package with public API types: `RelationCatalogService`, `RelationCatalogDefinition`, `RelationCatalogId`, `RelationQuery`, `RelationKindSignature`. (New modules use `com.lorevault.{domain}` — no `api` segment.)
- [x] `@ApplicationModule(type = CLOSED)` on `package-info.java`.
- [x] `resolve()` implements two-tier matching: exact match → create new. No signature match in MVP.
- [x] `resolve()` runs in `REQUIRES_NEW` transaction — catalog owns its PostgreSQL transaction boundary, never nested inside Neo4j transactions.
- [x] Database schema: `catalog_definition` + `catalog_definition_variant` + `catalog_definition_signature`. No observation table. No observation counts. No status columns. No pgvector in V1.
- [x] `RelationClaim` stores `catalogId` (UUID, nullable) and `definitionKey` (String, nullable). Removes `provisionalRelTypeId` and `resolutionStatus`.
- [x] `definitionKey` uses `R:` namespace prefix (e.g., `R:is_a_member_of`).
- [x] `RelationClaimPersistenceService` calls `catalogService.resolve()` before Neo4j persistence, stores `catalogId` + `definitionKey` on the claim. Degrades gracefully on catalog failure (catalogId=null, definitionKey keeps extracted value).
- [x] `EmbeddingFunction` interface defined in `com.lorevault.catalog` — catalog owns the contract, core provides the implementation. No shared `lorevault-ai` module.
- [x] Integration tests with Testcontainers PostgreSQL verify idempotency and matching logic.
- [ ] Spring Modulith verification passes. (ModulithVerificationTest does not exist yet — `@ApplicationModule(CLOSED)` is in place, but no runtime verification test.)
- [x] ArchUnit boundary rules pass. (Updated to scan `com.lorevault.catalog` + `com.lorevault.api`; added `catalog_internal_must_not_be_accessed_from_outside` rule.)
- [x] `LoreVaultApiApplication` DataSource auto-config exclusion addressed — kept `DataSourceAutoConfiguration` exclusion (catalog manages its own DataSource via `CatalogConfig`).
- [x] Flyway `locations` scoped to `classpath:db/migration/catalog` via `CatalogConfig.catalogFlyway` bean.
- [x] Empty `com.lorevault.api.catalog` package — did not exist, no action needed.

## Links

- `docs/planning/2026-05-07T1917_relation-evidence-harvesting.md` — relation evidence harvesting design and extraction context
- `docs/planning/2026-04-30T1237_qa-retrieval-quality-validation.md` — relation questions that should guide catalog usefulness
- `docs/planning/2026-04-30T1237_concept-resolution-lane.md` — Concept entity lane, needed for Concept-targeting relation signatures
- `docs/concepts/Entity-Event-Claim-model.md` — current entity/event/claim model
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — broader graph process context
- `docs/patterns/ingestion/triad-analysis.md` — triad normalization pipeline that will consume catalog outputs
- `docs/brainstorm/architecture/2026-05-11T2027_orchestration-domain-separation.md` — catalog as first closed internal module

## Implementation Notes (M0 + M1)

### Deviations from Plan

| Planned | Implemented | Reason |
|---------|-------------|--------|
| `PostgresCatalogSchemaInitializer` class | Flyway migration only (no Java initializer) | Flyway `baselineOnMigrate=true` + `V1__catalog_definition.sql` handles schema creation. No need for a separate Java initializer. |
| `CatalogDisabledConfiguration` in M2 | Moved to M0/M1 as `NoOpRelationCatalogService` | Needed immediately so the app starts without PostgreSQL when catalog is disabled. All methods throw `UnsupportedOperationException`. Renamed from `*Configuration` to `*Service` because it's a `@Service` implementation, not a config class. |
| `InMemoryRelationCatalogStore` as M0-only | Kept alongside `PostgresRelationCatalogStore` | `InMemoryRelationCatalogStore` has no `@ConditionalOnProperty` — it's available for unit testing but not activated in production profiles. `PostgresRelationCatalogStore` is `@ConditionalOnProperty(havingValue="true")`. |
| `resolveAll` method on `RelationCatalogService` | Not implemented | YAGNI — no consumer needs batch resolution yet. |
| `displayName` heuristic (capitalize first raw name) | Not implemented | Deferred to janitor task. `create()` uses `rawName` if present, falls back to `definitionKey`. |
| Synonym/variant table | `catalog_definition_variant` exists but no merge/synonym logic | Table is write-only for now — stores raw names for future disambiguation. |
| Backfill for orphaned claims | Not implemented | Greenfield project — no orphaned claims exist yet. Deferred to M2 or later. |
| Observability (metrics, health indicator) | Not implemented | Overengineering for current scale. Deferred to M2. |
| Catalog lifecycle (promotion, review status) | Not implemented | No value yet. Deferred to M4+. |
| Signature index | Commented out in V1 schema | Deferred to M3 — signature matching isn't used until embedding similarity is available. |
| `ModulithVerificationTest` | Does not exist | `@ApplicationModule(CLOSED)` is in place on `package-info.java`. ArchUnit boundary rules enforce the same constraints. A runtime Modulith verification test can be added later. |

### Key Implementation Decisions

1. **DataSource exclusion kept.** `LoreVaultApiApplication` still excludes `DataSourceAutoConfiguration`, `FlywayAutoConfiguration`, and `HibernateJpaAutoConfiguration`. The catalog module manages its own `DataSource` via `CatalogConfig` (conditional on `lorevault.catalog.enabled=true`). This avoids Spring Boot trying to auto-configure a default DataSource when no `spring.datasource.url` is set.

2. **Degradation mode in `RelationClaimPersistenceService`.** The catalog `resolve()` call is wrapped in try/catch. On failure (catalog disabled, PostgreSQL down, etc.), `catalogId` is set to null and `definitionKey` keeps the extracted value. The pipeline never blocks on catalog failure.

3. **`ON CONFLICT DO NOTHING` moved from M2 to M1.** Idempotent `findOrCreate` is essential for correctness, not hardening. Concurrent ingestion calls for the same `definitionKey` must produce exactly one definition row.

4. **`definitionKey` format changed.** The old `R:provisional.is_a_member_of` format (from `generateProvisionalRelTypeId`) is now `R:is_a_member_of` (from `generateDefinitionKey`). The `provisional` qualifier is dropped because the catalog assigns stable identities.

5. **Neo4j indexes renamed.** `relation_claim_chapter_reltype` → `relation_claim_chapter_defkey`, `relation_claim_book_reltype` → `relation_claim_book_defkey` — matching the field rename from `provisionalRelTypeId` to `definitionKey`.

6. **ArchUnit rules updated.** `ModulithBoundaryArchitectureTest` now scans both `com.lorevault.api` and `com.lorevault.catalog`. The `catalog_must_not_depend_on_ingestion_or_web` rule references the actual `com.lorevault.catalog..` package. A new `catalog_internal_must_not_be_accessed_from_outside` rule enforces that no class outside `com.lorevault.catalog` may depend on `com.lorevault.catalog.internal..`.

7. **Testcontainers PostgreSQL IT.** `PostgresRelationCatalogStoreIT` uses manual DataSource/Flyway/JdbcClient setup (no Spring context) for fast, focused integration tests. 8 test methods covering create, findById, findByDefinitionKey, idempotent creation, enrichment, and empty results.

8. **Component scanning fix (C1).** `@SpringBootApplication` on `com.lorevault.api` doesn't scan `com.lorevault.catalog`. Added `@ComponentScan(basePackages = "com.lorevault")` to `LoreVaultApiApplication` so catalog beans are discovered.

9. **JDBC transaction manager (C2).** `CatalogConfig` now provides a `catalogTransactionManager` bean (`DataSourceTransactionManager`) and all `@Transactional` annotations on `RelationCatalogServiceImpl` are qualified with `transactionManager = "catalogTransactionManager"`. Without this, `REQUIRES_NEW` would silently bind to the Neo4j transaction manager.

10. **HikariDataSource (H1).** Replaced `DriverManagerDataSource` with `HikariDataSource` via `DataSourceBuilder.create().type(HikariDataSource.class)` for connection pooling.

11. **Read-only method transactions (H2).** `findByKey()` and `findByDefinitionKey()` changed from `REQUIRES_NEW` to `@Transactional(readOnly = true, propagation = SUPPORTS, transactionManager = "catalogTransactionManager")`.

12. **NoOpRelationCatalogService (H3).** Renamed from `CatalogDisabledConfiguration` — the class is a `@Service` implementation, not a configuration class.

13. **Narrowed degradation catch (L7).** Changed `catch (Exception e)` to `catch (UnsupportedOperationException | DataAccessException e)` in `RelationClaimPersistenceService` to avoid swallowing programming errors.

14. **Removed baselineOnMigrate (M1).** Flyway config no longer includes `.baselineOnMigrate(true)` — the V1 migration uses `CREATE TABLE IF NOT EXISTS` for idempotency.
