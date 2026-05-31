# LoreVault Codebase Topology

**Status:** Present-state

Documents the current multi-module structure, known internal coupling, and shared
domain model boundaries in `lorevault-core`. Use this as a reference when adding new
packages, cross-package calls, or domain entities.

---

## Module Structure

Three Maven modules with strict dependency direction:

```
lorevault-web → lorevault-core → lorevault-catalog
```

- `lorevault-web` depends on `lorevault-core` (and transitively on `lorevault-catalog`).
- `lorevault-core` depends on `lorevault-catalog` (public API only).
- `lorevault-catalog` depends on **nothing** in LoreVault — only Spring Boot, JDBC, Flyway, PostgreSQL.

`lorevault-core` uses eight top-level feature packages under `com.lorevault.api`:

- `ai`
- `common`
- `config`
- `graph`
- `health`
- `library`
- `orchestration`
- `search`

Representative current internal package map:

| Top-level package | Current internal shape |
|---|---|---|
| `ai` | `embedding`, `infrastructure`, `llm`, `telemetry` |
| `common` | `error` |
| `graph` | `collective`, `event`, `individual`, `location`, `mention`, `object`, `relation`, `timeline` |
| `library` | `book`, `chapter`, `chunk`, `series`, `service`, `universe` |
| `orchestration` | `consolidation`, `job`, `pipeline`, `signals`, `submission`, `triad` |
| `search` | `extraction`, `model`, `rag`, `semantic` |

`lorevault-catalog` uses a single top-level package under `com.lorevault.catalog`:

| Top-level package | Current internal shape |
|---|---|
| `catalog` | `internal` (store, service, config, data source properties) |

The `internal` package is not exported — the public API is the `com.lorevault.catalog` package
surface: `RelationCatalogService`, `RelationCatalogDefinition`, `RelationCatalogId`,
`RelationQuery`, `RelationKindSignature`, and `EmbeddingFunction`. This boundary is enforced
by `@ApplicationModule(type = CLOSED)` and ArchUnit rules.

Legacy internal directories from the old layered shape (`application`, `domain`,
`entities`, and similar) are transitional leftovers only and are not part of the
canonical topology once empty.

---

## Known Intra-Module Coupling

These bidirectional couplings exist within `lorevault-core` and are tracked as
technical debt:

- `library ↔ graph` — the library management and graph packages reference
  each other.

These are known constraints, not patterns to follow or extend.

---

## Cross-Module Boundaries

The catalog module is a **closed module** — its `internal` package is not accessible
from outside. The only legal integration point is the `RelationCatalogService` interface.

- `lorevault-core` calls `RelationCatalogService.resolve()` before persisting `RelationClaim`
  to Neo4j. The catalog call runs in its own PostgreSQL transaction (`REQUIRES_NEW`), never
  nested inside a Neo4j transaction.
- When the catalog is disabled (`lorevault.catalog.enabled=false`), `NoOpRelationCatalogService`
  throws `UnsupportedOperationException` on every method. Callers degrade gracefully
  (`catalogId=null`, `definitionKey` retains the extracted value).
- The catalog manages its own `DataSource`, `JdbcTransactionManager`, and Flyway migration
  independently of the main application's Neo4j configuration.

---

## Shared Domain Models

`Chapter`, `Scene`, and `Chunk` already cross all package boundaries in `lorevault-core`.
This constraint exists because the ingestion pipeline, AI integration, and query layers
all operate on the same core entities. It is a known cost of the current architecture,
not a precedent for new shared types.

---

## Contributor Constraints

**Cross-module dependency direction** — `lorevault-core` must not import from
`lorevault-web`. `lorevault-catalog` must not import from `lorevault-core` or
`lorevault-web`. The legal dependency direction is:
`lorevault-web` → `lorevault-core` → `lorevault-catalog`.

Any dependency in the reverse direction is a build cycle and a defect.

**Do not deepen known couplings** — Do not add new cross-package method calls between
known coupled areas such as `library ↔ graph`. Keep `ai` narrow to generic LLM
infrastructure rather than feature-owned ingestion workflow. If new coordination is
needed, introduce an event instead.

**Do not add new shared domain models** — When new pipeline stages or services need to
communicate about domain concepts, pass IDs or minimal DTOs across package boundaries
instead of creating new entity classes that all packages import.
