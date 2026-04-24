# LoreVault Codebase Topology

**Status:** Present-state

Documents the current multi-module structure, known internal coupling, and shared
domain model boundaries in `lorevault-core`. Use this as a reference when adding new
packages, cross-package calls, or domain entities.

---

## Module Structure

`lorevault-web` and `lorevault-core` are the two Maven modules. `lorevault-web` depends
on `lorevault-core`. There is no third module.

---

## Known Intra-Module Coupling

These bidirectional couplings exist within `lorevault-core` and are tracked as
technical debt:

- `library ↔ content` — the library management and content management packages reference
  each other.

These are known constraints, not patterns to follow or extend.

---

## Shared Domain Models

`Chapter`, `Scene`, and `Chunk` already cross all package boundaries in `lorevault-core`.
This constraint exists because the ingestion pipeline, AI integration, and query layers
all operate on the same core entities. It is a known cost of the current architecture,
not a precedent for new shared types.

---

## Contributor Constraints

**Cross-module dependency direction** — `lorevault-core` must not import from
`lorevault-web`. Any dependency in that direction is a build cycle and a defect. This
includes Spring MVC annotations in core, `@RestController` in core, or any import of a
`lorevault-web` class from within `lorevault-core`.

**Do not deepen known couplings** — Do not add new cross-package method calls between
known coupled areas such as `library ↔ content`. Keep `ai` narrow to generic LLM
infrastructure rather than feature-owned ingestion workflow. If new coordination is
needed, introduce an event instead.

**Do not add new shared domain models** — When new pipeline stages or services need to
communicate about domain concepts, pass IDs or minimal DTOs across package boundaries
instead of creating new entity classes that all packages import.
