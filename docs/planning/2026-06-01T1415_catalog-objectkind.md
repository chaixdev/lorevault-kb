# Catalog.ObjectKind — Cataloged Concept Type Vocabulary

**Status:** PLANNING (no-op first, real backend deferred)  
**Last Updated:** June 1, 2026

## Summary

Extend the `lorevault-catalog` module with an `ObjectKind` subdomain that catalogs concept type vocabulary (species, technology, doctrine, role, artifact-class, etc.) using the same pattern as `RelationKind`: exact-match → pgvector semantic match → create, backed by PostgreSQL with `ON CONFLICT DO NOTHING` idempotency.

**Ship strategy:** Define the `ObjectKindCatalogService` public API interface and `NoOpObjectKindCatalogService` now (unblocks Concept entity lane). Real PostgreSQL backend ships later as a transparent swap.

## Motivation

The Concept entity lane needs a type vocabulary — the LLM classifies concepts into categories (species, technology, doctrine, etc.). Free-form text causes query fragility (`WHERE c.conceptType = 'species'` breaks on variant labels). A closed enum is rigid. A cataloged vocabulary provides:

- Stable `definition_key` references (query-safe)
- Semantic matching (LLM says "biological_classification" → matches "species")
- Evolvability (new types cataloged on first encounter, no code changes)
- Same modular boundary as `RelationKind` (separate `lorevault-catalog` module, `REQUIRES_NEW` transaction)

## Design

### Public API (ships now)

```
lorevault-catalog/src/main/java/com/lorevault/catalog/
├── ObjectKindCatalogService.java         (public API interface)
├── ObjectKindCatalogDefinition.java      (public record)
├── ObjectKindQuery.java                  (public record)
├── ObjectKindCatalogId.java              (public value type UUID)
└── internal/
    └── NoOpObjectKindCatalogService.java  (returns raw LLM label)
```

### Interface

```java
public interface ObjectKindCatalogService {
    ObjectKindCatalogDefinition resolve(ObjectKindQuery query);
    Optional<ObjectKindCatalogDefinition> findByKey(ObjectKindCatalogId id);
    Optional<ObjectKindCatalogDefinition> findByDefinitionKey(String definitionKey);
}

public record ObjectKindCatalogDefinition(
    ObjectKindCatalogId id,
    String definitionKey,
    String displayName,
    String description,
    List<String> rawNameVariants,
    Instant created, Instant updated, Instant lastSeen
) {}

public record ObjectKindQuery(String definitionKey, String rawName, String description) {}

public record ObjectKindCatalogId(UUID value) {}
```

### NoOp implementation (ships now)

```java
@ConditionalOnMissingBean(ObjectKindCatalogService.class)
public class NoOpObjectKindCatalogService implements ObjectKindCatalogService {
    @Override
    public ObjectKindCatalogDefinition resolve(ObjectKindQuery query) {
        String key = query.definitionKey() != null ? query.definitionKey() : "concept:" + query.rawName();
        return new ObjectKindCatalogDefinition(
            new ObjectKindCatalogId(UUID.randomUUID()),
            key,
            query.rawName() != null ? query.rawName() : key,
            query.description(),
            query.rawName() != null ? List.of(query.rawName()) : List.of(),
            Instant.now(), Instant.now(), Instant.now()
        );
    }
}
```

No state, no dependencies, no PostgreSQL. The catalog interface is a pass-through for the raw LLM label. `ConceptMention` stores `catalogId` and `definitionKey` — when the real catalog ships, those fields keep working, just with persistent catalog IDs.

### Real backend (deferred)

```
lorevault-catalog/src/main/java/com/lorevault/catalog/
└── internal/
    ├── ObjectKindCatalogServiceImpl.java   (@ConditionalOnProperty, REQUIRES_NEW)
    ├── ObjectKindCatalogStore.java         (package-private interface)
    └── PostgresObjectKindCatalogStore.java (NamedParameterJdbcTemplate + pgvector)

lorevault-catalog/src/main/resources/db/migration/catalog/
└── V2__catalog_object_kind.sql
```

### PostgreSQL schema (deferred)

```sql
CREATE TABLE catalog_object_kind (
    id              UUID PRIMARY KEY,
    definition_key  TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    description     TEXT,
    created         TIMESTAMPTZ NOT NULL,
    updated         TIMESTAMPTZ NOT NULL,
    last_seen       TIMESTAMPTZ NOT NULL,
    embedding       vector(1536)
);
CREATE INDEX idx_catalog_object_kind_embedding_hnsw
    ON catalog_object_kind USING hnsw (embedding vector_cosine_ops);

CREATE TABLE catalog_object_kind_variant (
    definition_id   UUID NOT NULL REFERENCES catalog_object_kind(id),
    raw_name        TEXT NOT NULL,
    PRIMARY KEY (definition_id, raw_name)
);
```

No `catalog_object_kind_signature` table — object kinds don't have subject/object pairs like relation kinds do.

### Three-tier resolution (deferred — same as RelationKind)

1. **Exact match** on `definition_key` (e.g., `concept:species`)
2. **Semantic match** via pgvector cosine similarity (e.g., LLM says "biological_classification" → matches "species" at 0.15 distance)
3. **Create** new definition if nothing matches — `ON CONFLICT DO NOTHING`, first-write-wins

**Embedding text format:** `"{description}: {rawName}"` — same pattern as relation catalog.

### Transaction boundary (deferred)

Catalog resolution uses `REQUIRES_NEW` on `catalogTransactionManager` — concept type definitions are durable observations that survive downstream Neo4j rollbacks. Same boundary as `RelationCatalogServiceImpl.resolve()`.

### Module registration (deferred)

Update `CatalogConfig` to wire `PostgresObjectKindCatalogStore` and `CatalogHealthIndicator` to include `catalog_object_kind` table.

---

## Integration points

### Consumer: ConceptPersistenceService

```java
ObjectKindQuery query = new ObjectKindQuery(
    "concept:" + normalizeConceptType(extracted.conceptType()),
    extracted.conceptType(),
    null
);
ObjectKindCatalogDefinition def = catalogService.resolve(query);
// ConceptMention stores def.id().value() and def.definitionKey()
```

### Consumer: Concept consolidation

During consolidation, concepts with the same `catalogId` (same type) can be clustered — useful if subtypes matter for identity. v1 uses name-only clustering; v2 may add `catalogId` to the composite key.

---

## Implementation phases

| Phase | Files | When |
|-------|-------|------|
| **Now** | `ObjectKindCatalogService`, `ObjectKindCatalogDefinition`, `ObjectKindQuery`, `ObjectKindCatalogId`, `NoOpObjectKindCatalogService` (5 files) | Unblocks Concept entity lane P3 |
| **Deferred** | `ObjectKindCatalogServiceImpl`, `ObjectKindCatalogStore`, `PostgresObjectKindCatalogStore`, `V2__catalog_object_kind.sql`, update `CatalogConfig`, `CatalogHealthIndicator` (6 files) | After pipeline ships, transparent swap |

---

## Open questions

1. **Should `ConceptMention` store catalogId from day one?** Yes — field type is `UUID`, no-op fills synthetic UUID. When real catalog ships, field doesn't change, just gets persistent IDs.
2. **Should object kinds be pre-seeded?** Bootstrap a few canonical types (species, technology, doctrine, role, artifact-class) for query stability. The LLM prompt already steers toward these categories.
3. **Can other entity types use ObjectKind?** Potentially — `Location.kind`, `Object.type`, and `Collective.collectiveType` could all benefit from cataloged vocabularies. Out of scope for v1.

---

## Links

- Concept entity lane: `docs/planning/2026-06-01T1400_concept-entity-lane.md`
- Relation catalog module: `lorevault-catalog/src/main/java/com/lorevault/catalog/`
- ADR-012: Dual-database transaction boundary: `docs/adr/012-dual-database-transaction-boundary.md`
- Relation catalog plan: `docs/planning/2026-05-13T2027_relation-catalog-module.md`
