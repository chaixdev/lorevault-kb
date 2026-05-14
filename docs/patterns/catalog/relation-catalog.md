# Relation Catalog

**Status:** Present-state (M0 + M1 shipped)

Documents how the relation catalog module works today: its public contract, matching
strategy, dual-database transaction boundary, degradation mode, and module isolation.

For the architectural decision behind the dual-database approach, see
[ADR-012](../../adr/012-dual-database-transaction-boundary.md).

For the full milestone progression and future plans (M2 hardening, M3 embedding matching,
M4 graph edge projection), see the
[planning doc](../../planning/relation-catalog-module.md).

---

## What the Catalog Is

The catalog is a **reverse dictionary** for relation kinds. It answers one question:

> Given this description of a relationship I found in the text (its name, the entity
> kinds involved, what the LLM says it means), what stable identity should I use?

The catalog owns the **definition** of relation kinds — their stable identity, display
name, entity kind signatures, description, and (in M3) semantic embedding. The graph
owns the **provenance** — who said what about whom, with what evidence.

---

## Module Structure

```
lorevault-web → lorevault-core → lorevault-catalog
```

`lorevault-catalog` is a **closed module**:

- Public API surface: `com.lorevault.catalog` package — `RelationCatalogService`,
  `RelationCatalogDefinition`, `RelationCatalogId`, `RelationQuery`,
  `RelationKindSignature`, `EmbeddingFunction`
- Internal implementation: `com.lorevault.catalog.internal` — not exported
- Boundary enforced by `@ApplicationModule(type = CLOSED)` and ArchUnit rules
- No dependency on `lorevault-core` or `lorevault-web`

New modules should use `com.lorevault.{domain}` package convention (no `api` segment).
The existing `com.lorevault.api.*` packages are legacy.

---

## Public Contract

```java
public interface RelationCatalogService {
    RelationCatalogDefinition resolve(RelationQuery query);
    Optional<RelationCatalogDefinition> findByKey(RelationCatalogId id);
    Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey);
}
```

- `resolve()` — find-or-create: exact match on `definitionKey`, create new if not found
- `findByKey()` / `findByDefinitionKey()` — read-only lookups

### definitionKey Convention

`definitionKey` values use a namespace prefix:

- **Relation kinds:** `R:` prefix — e.g., `R:is_a_member_of`, `R:leads`, `R:betrays`
- **Future entity kinds:** `E:` prefix (not yet implemented)

Normalization: `R:` + lowercase + underscores from the raw name.
`"is a member of"` → `R:is_a_member_of`

---

## Matching Strategy (M0–M1)

Two-tier matching, applied in order by `resolve()`:

```
1. NORMALIZE rawName → definitionKey (R: prefix + lowercase + underscores)
2. EXACT MATCH: SELECT definition WHERE definition_key = ?
   ├── Found → update last_seen, return definition
   └── Not found → continue
3. CREATE NEW: INSERT definition + signature + variant, return new definition
```

Signature-based disambiguation is deferred to M3 (embedding similarity via pgvector).
The `(subjectKind, objectKind)` signature is too coarse for disambiguation — an
`Individual→Individual` signature would match "leads", "betrayed", "is married to",
and "trained under" as semantically unrelated relations.

---

## Dual-Database Transaction Boundary

The catalog writes to PostgreSQL. Core writes to Neo4j. These are **never nested**.

```
// Step 1: Catalog resolves in its own transaction (PostgreSQL)
catalogId = catalogService.resolve(query)  // REQUIRES_NEW, separate PostgreSQL tx

// Step 2: Neo4j persistence with resolved catalogId
@Transactional  // Neo4j transaction only
persistExtractedRelationClaims(catalogId) {
    claim.setCatalogId(catalogId)
    neo4jRepository.save(claim)
}
```

- `resolve()` runs in `@Transactional(propagation = REQUIRES_NEW, transactionManager = "catalogTransactionManager")`
- Read methods use `@Transactional(readOnly = true, propagation = SUPPORTS, transactionManager = "catalogTransactionManager")`
- The catalog manages its own `DataSource`, `DataSourceTransactionManager`, and Flyway — all conditional on `lorevault.catalog.enabled=true`
- The Neo4j transaction manager is declared `@Primary` via `Neo4jTransactionManagerPrimaryConfiguration` in `lorevault-core`. When both Neo4j and JDBC transaction managers exist, `TransactionOperations` resolves to the Neo4j one, which `Neo4jTemplate` requires.

If the Neo4j write fails after a successful catalog INSERT, the catalog has a phantom
definition. This is harmless — `ON CONFLICT DO NOTHING` makes catalog writes idempotent.

---

## Degradation Mode

When the catalog is disabled or unavailable, the ingestion pipeline continues:

```java
try {
    definition = catalogService.resolve(query);
    claim = claim.withCatalogId(definition.id()).withDefinitionKey(definition.definitionKey());
} catch (UnsupportedOperationException | DataAccessException e) {
    log.warn("Catalog resolution failed, degrading: {}", e.getMessage());
    claim = claim.withCatalogId(null).withDefinitionKey(query.definitionKey());
}
```

- `UnsupportedOperationException` — catalog is disabled (`NoOpRelationCatalogService`)
- `DataAccessException` — PostgreSQL is down or query failed
- Result: `catalogId=null`, `definitionKey` retains the extracted value
- Claims without `catalogId` are not "orphaned" — they simply lack a catalog reference

---

## Idempotency

`resolve()` must be idempotent on the miss path. If two concurrent ingestion calls both
miss on "is allied with," both try to INSERT. Exactly one wins — `ON CONFLICT DO NOTHING`
on the unique `definition_key` constraint handles this. The loser re-reads the existing row.

```sql
INSERT INTO catalog_definition (id, definition_key, display_name, description, created, updated, last_seen)
VALUES (:id, :definitionKey, :displayName, :description, :now, :now, :now)
ON CONFLICT (definition_key) DO NOTHING
```

---

## Database Schema

Three tables in PostgreSQL, managed by Flyway:

- `catalog_definition` — stable identity, `definition_key` (unique), `display_name`, `description`, timestamps
- `catalog_definition_variant` — raw name variants per definition (composite PK: `definition_id, raw_name`)
- `catalog_definition_signature` — `(subjectKind, objectKind)` pairs per definition (composite PK: `definition_id, subject_kind, object_kind`)

Signature index deferred to M3 — not needed until embedding similarity is available.

---

## Configuration

```yaml
lorevault:
  catalog:
    enabled: false  # set to true to enable PostgreSQL-backed catalog
    datasource:
      url: jdbc:postgresql://localhost:5432/lorevault_catalog
      username: lorevault
      password: lorevault_secret
```

When `enabled=false` (default), `NoOpRelationCatalogService` is active — all methods
throw `UnsupportedOperationException`. The pipeline degrades gracefully.

When `enabled=true`, `CatalogConfig` creates:
- `catalogDataSource` (HikariCP)
- `catalogTransactionManager` (`DataSourceTransactionManager`)
- `catalogNamedParameterJdbcTemplate` (`NamedParameterJdbcTemplate`)
- `catalogFlyway` (migrates `classpath:db/migration/catalog`)

---

## Embedding Integration (M3)

The catalog needs embedding calls for semantic matching, but must not depend on
Spring AI. The `EmbeddingFunction` interface solves this:

```java
// In lorevault-catalog — catalog owns the contract
@FunctionalInterface
public interface EmbeddingFunction {
    float[] embed(String text);
}
```

Core provides the implementation by wrapping Spring AI's `EmbeddingModel`. The catalog
has zero knowledge of Spring AI types. See
[ADR-012](../../adr/012-dual-database-transaction-boundary.md) for the full rationale.