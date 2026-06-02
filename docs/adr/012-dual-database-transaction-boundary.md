# 012 — Dual-Database Transaction Boundary and Catalog Module Isolation

**Status:** Accepted

**Date:** May 14, 2026

## Context

LoreVault's primary data store is Neo4j. The relation catalog module needs a relational database (PostgreSQL) for its definition store — structured data with unique constraints, idempotent upserts, and future pgvector similarity search.

This creates a dual-database architecture where:
- The catalog module writes to PostgreSQL
- The core/web modules write to Neo4j
- The ingestion pipeline calls both in sequence

The question is how to manage transactions across these two databases.

## Decision

1. **Each module owns its database transactions.** The catalog's `resolve()` method runs in `REQUIRES_NEW` against its own `JdbcTransactionManager`. Core's persistence methods run in Neo4j transactions via Spring Data Neo4j's `Neo4jTransactionManager`. These transactions are never nested.

2. **The catalog call is a side-effecting query that precedes Neo4j persistence.** The ingestion pipeline calls `catalogService.resolve()` first, gets a `catalogId` + `definitionKey`, then persists the `RelationClaim` to Neo4j. If the Neo4j write fails, the catalog has a phantom definition — but this is harmless because `ON CONFLICT DO NOTHING` makes catalog writes idempotent.

3. **The catalog degrades gracefully.** When the catalog is disabled or unavailable, `RelationClaimPersistenceService` catches `UnsupportedOperationException | DataAccessException` and persists the claim with `catalogId=null`. The pipeline never blocks on catalog failure.

4. **The catalog manages its own DataSource, transaction manager, and Flyway.** `CatalogConfig` creates a `HikariDataSource`, `DataSourceTransactionManager`, `JdbcClient`, and Flyway bean — all conditional on `lorevault.catalog.enabled=true`. The main application's `DataSourceAutoConfiguration` exclusion remains in place because there is no default `spring.datasource.url`.

5. **The `EmbeddingFunction` interface pattern** — the catalog defines a `@FunctionalInterface` (`float[] embed(String text)`) that core implements by wrapping Spring AI's `EmbeddingModel`. This keeps the catalog free of Spring AI dependencies. A shared `lorevault-ai` module was rejected because the catalog's AI need is narrow (one operation) and the function interface is a stronger boundary than a shared module.

## Alternatives Considered

### Shared DataSource (single database)

Use Neo4j for everything, or use PostgreSQL for everything. Rejected because:
- Neo4j is not a relational database — unique constraints, idempotent upserts, and future pgvector queries need PostgreSQL.
- Moving the entire application to PostgreSQL would abandon the graph model that is LoreVault's core value.

### Distributed transactions (2PC / JTA)

Use XA transactions to coordinate PostgreSQL and Neo4j writes. Rejected because:
- Spring Data Neo4j does not support XA.
- 2PC adds latency and failure modes that are disproportionate to the benefit.
- The catalog's idempotent writes make distributed coordination unnecessary.

### Shared `lorevault-ai` module

Extract AI infrastructure into a shared module that both core and catalog depend on. Rejected because:
- The catalog needs exactly one operation: `embed(String) → float[]`.
- A shared module would expose the full Spring AI surface to the catalog, weakening the boundary.
- No dumping-ground risk with a function interface — the catalog can't shove random things into an interface it owns.
- Extraction is justified when a third module genuinely needs the full AI stack.

### Nested transactions

Wrap the catalog call inside the Neo4j transaction. Rejected because:
- A Spring Data Neo4j `@Transactional` does not cover JDBC writes to PostgreSQL.
- Wrapping gives a false sense of atomicity — if the Neo4j write fails, the PostgreSQL write is not rolled back.
- `REQUIRES_NEW` ensures the catalog's transaction commits or rolls back independently.

## Implications

- `RelationClaim` may have `catalogId=null` for claims ingested while the catalog was disabled or unavailable. These are not "orphaned" — they simply lack a catalog reference. A future backfill job can re-process them.
- Phantom definitions (catalog INSERT succeeded, Neo4j write failed) are harmless — they're unreferenced rows that a cleanup job can sweep.
- The `@ComponentScan(basePackages = "com.lorevault")` on `LoreVaultApiApplication` is required because the catalog module lives in `com.lorevault.catalog`, which is not a sub-package of `com.lorevault.api`.
- New modules should use `com.lorevault.{domain}` package convention (no `api` segment). The existing `com.lorevault.api.*` packages are legacy.