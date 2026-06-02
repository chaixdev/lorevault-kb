# LoreVault Module and Domain Model Conventions

**Scope:** LoreVault-specific rules for the multi-module structure, package coupling
constraints, domain model boundaries, and graph schema management.

For generic package boundary rules, see [coding-standards.md](coding-standards.md).

---

## Module Dependency Direction

The legal dependency direction is:

```
lorevault-web → lorevault-core → lorevault-catalog
```

`lorevault-core` must not import from `lorevault-web`. `lorevault-catalog` must not import
from `lorevault-core` or `lorevault-web`. Any dependency in the reverse direction is a
build cycle and a defect. This includes: Spring MVC annotations in core,
`@RestController` in core, or any import of a `lorevault-web` or `lorevault-core` class
from within `lorevault-catalog`.

The catalog module (`lorevault-catalog`) is a **closed module** — its `internal` package
is not exported. The only legal integration point is the public API surface in
`com.lorevault.catalog`: `RelationCatalogService`, `RelationCatalogDefinition`,
`RelationCatalogId`, `RelationQuery`, `RelationKindSignature`, and `EmbeddingFunction`.

New modules should use the `com.lorevault.{domain}` package convention (no `api` segment).
The existing `com.lorevault.api.*` packages are a legacy convention — a future task will
rename them to drop the `api` segment.

---

## Known Coupling Risks and Shared Model Constraints

The current topology — including bidirectional coupling between `library ↔ graph`
and the `Chapter`/`Scene`/`Chunk` shared model constraint — is documented with context
in [codebase-topology.md](../patterns/codebase-topology.md).

Do not add new cross-package method calls between the known coupled packages.
Do not re-expand `ai` into feature-owned orchestration workflow; keep it focused on
generic LLM infrastructure.
Do not add new shared domain models. See the topology doc for the current state
and rationale.

**Cross-lane type placement.** A domain type consumed by all entity consolidation lanes
(e.g., `BookConsolidationClaim`) must not live inside a single lane's package. Placing
a shared type inside one lane (e.g., `location/consolidation/book/`) creates an implicit
ownership claim that all other lanes violate via cross-package imports. Cross-lane types
belong in the consuming boundary's most stable package (e.g., `orchestration/consolidation`)
or in `common` if they have no feature-specific semantics.

## Package-Structure Direction

Within `lorevault-core`, the default internal package shape is capability-oriented.

- Keep the current top-level feature split under `com.lorevault.api`.
- Inside a feature, prefer packages that communicate semantic ownership such as
  `scene`, `chunk`, `resolution`, `rag`, `semantic`, `book`, or `universe`.
- Treat old `application/domain/infrastructure` directories as retired defaults, not as
  a template for new work.

Scoped exceptions are acceptable when they remain semantically tight:

- `graph/timeline` keeps its local layered substructure because it is already a dense,
  self-contained mechanism with clear internal roles.
- shared support seams such as `ai/infrastructure`, `orchestration/signals`,
  `orchestration/pipeline`, or `search/model` are acceptable when they
  prevent false ownership or avoid back-edge cycles between capability packages.

Remove empty legacy package directories after semantic moves so the source tree does not
advertise a stale architecture.

---

## Graph Schema Management

All Neo4j index and constraint declarations belong in `GraphSchemaInitializer`.
Do not scatter index or constraint creation in service methods or repository queries.

When a new `@Query` method filters on a property, add the corresponding index to
`GraphSchemaInitializer` in the same PR.

---

## Temporal Edge Semantics

Neo4j relationship direction for temporal and narrative edges must conform to the
canonical polarity defined in [temporal-relation-semantics.md](../concepts/temporal-relation-semantics.md).
This applies to relationships in the scene, location, and individual resolution pipelines.

The `CONTAINS` relation must not be misread as a timeline ordering edge.
See `temporal-relation-semantics.md` for the full Allen relation model and
canonical polarity rules.
