# LoreVault Module and Domain Model Conventions

**Scope:** LoreVault-specific rules for the multi-module structure, package coupling
constraints, domain model boundaries, and graph schema management.

For generic package boundary rules, see [coding-standards.md](coding-standards.md).

---

## Module Dependency Direction

`lorevault-web` → `lorevault-core` is the only legal cross-module dependency.

`lorevault-core` must not import from `lorevault-web`. Any dependency in that direction
is a build cycle and a defect. This includes: Spring MVC annotations in core,
`@RestController` in core, or any import of a `lorevault-web` class from within
`lorevault-core`.

---

## Known Coupling Risks and Shared Model Constraints

The current topology — including bidirectional coupling between `library ↔ content`
and the `Chapter`/`Scene`/`Chunk` shared model constraint — is documented with context
in [codebase-topology.md](../patterns/codebase-topology.md).

Do not add new cross-package method calls between the known coupled packages.
Do not re-expand `ai` into feature-owned ingestion workflow; keep it focused on
generic LLM infrastructure.
Do not add new shared domain models. See the topology doc for the current state
and rationale.

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

The `MENTIONS` relation must not be misread as a timeline ordering edge.
See `temporal-relation-semantics.md` for the full Allen relation model and
canonical polarity rules.
