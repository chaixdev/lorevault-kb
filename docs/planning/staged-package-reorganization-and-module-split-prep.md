# Reorganize source packages for better browsability and semantic guidance

**Status:** PARKED

## Summary

The top-level source package split already reflects the main feature areas of the product well, but several feature packages have become internally flat and harder to browse as they have grown.

This planning item now also tracks a combined staged direction for sequencing package cleanup with the likely later split into `web`, `core`, and an optional shared `api` module.

The current working direction is not to perform broad browsability-driven package moves first. Instead, the work should be staged so boundary-clarifying cleanup happens before module extraction, and broader package cleanup follows once module ownership is stable.

## Problem

The current Java source layout is largely coherent at the top level, but some feature packages have accumulated too many files with mixed roles in a single flat namespace.

That creates several forms of friction:

- it becomes slower to scan a package and understand what kinds of classes live there
- mixed responsibilities in one package weaken the semantic meaning of package boundaries
- new code has fewer structural cues about where it should belong
- package growth becomes accidental rather than guided by a repeatable organizational rule

The result is not that the feature split is wrong, but that some feature internals no longer communicate intent clearly enough.

At the same time, the repository is also a candidate for a later Maven module split. That introduces a second source of friction:

- some package moves would clarify eventual `web` vs `core` ownership
- some package moves would be mostly cosmetic and risk being done twice
- some shared DTO and contract types currently blur the module boundary more than the package boundary

Without an explicit staged plan, the repository risks doing readability cleanup and module-boundary cleanup in the wrong order.

## Product Context

- Better source-code browsability improves day-to-day development speed.
- Clear package semantics help new contributors and agents place new code more consistently.
- A more legible structure reduces the risk of important domain, transport, persistence, and orchestration concerns drifting together over time.
- A staged approach avoids churn from moving code for readability and then moving it again when module boundaries are enforced.

## Technical Context

The current production source layout is primarily feature-oriented at the top level under `com.lorevault.api`.

The repository is already a Maven reactor with a single runtime module, so future extraction into multiple modules is structurally possible even though the code currently lives under one production module.

Stable top-level areas currently include:

- `ingestion`
- `content`
- `search`
- `ai`
- `timeline`
- `library`
- `web`
- `config`
- `health`
- `support`

Existing naming already mixes feature-first structure with role-oriented naming such as `Service`, `Controller`, `Handler`, `Event`, and repository suffixes.

The strongest current internal structure already visible in the codebase includes:

- `web.command`, `web.query`, and `web.ui` separation
- feature-local classes with role suffixes such as `*Service`, `*Handler`, `*Controller`
- some focused subareas such as `search.entityextraction`

Recent audit findings also indicate:

- `web` is already grouped more clearly than most other areas and likely maps cleanly to a future `web` module
- `support` contains both likely shared contract types and catch-all utility or policy types
- `search` mixes transport DTOs, orchestration, and infrastructure concerns in the same feature area
- `ingestion` is still the clearest large mixed-responsibility package
- `content`, `ingestion`, and `timeline` are tightly coupled enough that broad pre-split reshuffling risks encoding the wrong boundaries
- shared DTOs and service signatures appear to be the main seam for a future `web` / `core` split

The most likely reorganization candidates identified so far are:

- `com.lorevault.api.ingestion` as the largest mixed-responsibility package
- `com.lorevault.api.support` as a catch-all shared package containing DTOs, utilities, and policy-like types
- `com.lorevault.api.web.ui` where controllers, forms, and view data structures are all part of the same UI area
- `com.lorevault.api.web.command.ingestion` where controllers, validation, extraction, builders, and response shaping all participate in the same command area
- `com.lorevault.api.search` where retrieval orchestration and extraction concerns may deserve clearer internal separation

## Scope

- Define a desired package-organization direction for production Java code.
- Define a staged sequence that combines package cleanup with later module-boundary work.
- Preserve the current top-level feature split unless a later review shows a specific exception is necessary.
- Identify which packages should remain flat because they are already homogeneous.
- Identify which packages should gain internal subpackages because they exceed a reasonable size and mix responsibilities.
- Establish a small stable vocabulary for subpackage naming so future growth stays legible.
- Capture candidate areas for phased reorganization work later.
- Identify which cleanup steps should happen before module extraction, during module extraction, and after module extraction.

## Out of Scope

- Performing the package moves now
- Performing the Maven module split now
- Renaming classes as part of broader domain modeling changes
- Large behavioral refactors that are only indirectly related to package layout
- Replacing the current top-level feature split with a layer-first architecture
- Documenting final canonical package rules before the structure is actually accepted and implemented

## Known Constraints / Prior Findings

- The top-level feature split appears broadly correct and should be preserved by default.
- Package growth beyond roughly 7-10 files deserves review when the contents are not highly uniform.
- Larger flat packages are acceptable when they contain one narrow kind of thing, such as DTOs, mappers, or similarly homogeneous types.
- The repository already uses a mixed but understandable style: feature-first organization with role-based naming inside features.
- `ingestion` appears to be the clearest current example of a package that mixes events, handlers, services, repositories, status models, and logging-related types.
- `support` currently behaves as a shared catch-all and likely needs sharper boundaries so only truly shared concerns remain there.
- `web` already contains a useful semantic split that should likely be strengthened rather than replaced.
- Repository naming currently shows some drift across `*Repository`, `*GraphRepository`, `*ReadRepository`, and `*WriteRepository` styles.
- DTO placement also appears mixed between feature-local usage and cross-cutting shared placement.
- `web.ui` and `web.command.ingestion` appear comparatively cohesive already and are weaker candidates for early package cleanup.
- The highest-value pre-module work is likely around clarifying shared DTO ownership rather than broad feature-internal reshuffling.
- Domain-free request and response models are lower-risk early move candidates than support types that directly depend on ingestion or domain enums and models.
- `ingestion`, `content`, and `timeline` appear tightly connected enough that broad internal movement before module boundaries are chosen would create move-twice risk.
- A separate `api` module is most plausible as a shared contracts jar, not automatically as a home for every interface or abstraction.

## Candidate Staged Direction

### Stage 0 — Boundary audit and ownership marking

Before moving code, identify which existing types are:

- clearly `web`-owned transport concerns
- clearly `core`-owned domain or orchestration concerns
- likely shared contracts
- still ambiguous and should wait

This stage should focus especially on:

- `support`
- search request and response DTOs
- error-response shaping
- service signatures that currently use transport-shaped DTOs directly

### Stage 1 — Narrow pre-split cleanup that clarifies boundaries

Only do package or type moves that directly reduce future module ambiguity.

The highest-value candidate areas are:

- narrowing `support` so obviously shared contracts are separated from catch-all helpers or policy-like types
- clarifying where search DTOs belong relative to search orchestration and transport-facing usage
- tightening any package naming or placement that makes `web` vs `core` ownership materially clearer

This stage should stay intentionally small. The goal is not full browsability cleanup; the goal is to remove the worst ownership ambiguity before module extraction.

### Stage 2 — Module extraction

After the narrow boundary-prep pass, use the clarified ownership lines to drive a split into:

- `web` for HTTP, UI, request handling, response shaping, and bootstrapping concerns
- `core` for business logic, orchestration, domain behavior, persistence, and infrastructure that is not itself transport-facing
- optional `api` only if a stable shared contracts surface is clearly justified

This stage should be treated as the primary architectural step. It should not wait on broad cosmetic package cleanup.

### Stage 3 — Post-split browsability cleanup inside stable modules

Once module ownership is settled, revisit larger browsability problems that are still worth solving inside the new module boundaries.

The strongest current candidates for that later cleanup remain:

- `ingestion`, especially if it still mixes events, handlers, repositories, services, and status models in one namespace
- `search`, especially if orchestration, infrastructure, and DTO concerns still sit too close together

At that point, cleanup becomes less likely to be undone by later architectural moves.

## Candidate Work Sequence

1. Audit and classify shared contract and support types by ownership risk.
2. Perform only the smallest package or type moves that clarify future module boundaries.
3. Split modules around the now-clearer `web` / `core` seam.
4. Revisit larger internal package cleanup within the resulting modules.

## Open Questions

- Which packages should remain intentionally flat even after review?
- Should feature-specific request and response models move out of `support` and live closer to their owning feature or web area?
- How much repository naming standardization is desirable before package moves begin?
- Should search extraction concerns remain under `search` or become a more explicitly named subarea?
- Should the eventual work be done feature by feature, or should a repo-wide package convention be codified first in a brainstorm or rules doc?
- Which existing support types are truly shareable contracts versus internal coupling artifacts that should stay with `core`?
- Should the eventual `api` module exist at all, or should shared contract extraction stop at a narrower set of DTOs?
- How much of `ingestion` should be reorganized before module extraction versus only after `core` exists as a stable home?

## Code Organization Guidance

This section records the agreed internal vocabulary and structural rules to apply when placing new code and when deciding what to move or split during each stage.

### Canonical subpackage vocabulary

When a feature package grows beyond roughly 10–15 public types, or when it clearly mixes transport concerns with domain or infrastructure concerns, use this vocabulary to introduce subpackages:

| Subpackage | What belongs there |
|---|---|
| `web.command` | HTTP command controllers, request models, response shaping, validation, file extraction |
| `web.query` | HTTP query controllers, read-path request and response models |
| `web.ui` | Server-side UI controllers, Thymeleaf view models, form objects |
| `application` | Orchestration services, coordinators, pipeline handlers, use-case services |
| `domain` | Core domain models, domain-specific rules, value objects |
| `infrastructure` | Repository implementations, graph clients, AI clients, external service adapters |

The existing `web.command`, `web.query`, and `web.ui` split already matches this vocabulary and should be preserved and strengthened rather than replaced.

### Allowed dependency direction

Dependencies must flow in one direction only:

```
web → application → domain
infrastructure → domain (implements interfaces defined in domain or application)
```

`web` must never import from `infrastructure` directly. `domain` must never import from `web` or `infrastructure`. Violation of this direction is a package boundary smell, not just a style issue.

### Type ownership rule

Every type must have a single owning feature package. A type that cannot be assigned to one feature is a shared contract candidate — it belongs in a minimal shared area, not a catch-all like `support`. Types placed in `support` should be evaluated against: does any two distinct features genuinely need this, or is it internal coupling disguised as sharing?

### Naming vocabulary

Use these suffixes consistently. Introducing a new pattern requires an explicit decision.

| Suffix | Meaning |
|---|---|
| `*Controller` | HTTP entry point, command or query |
| `*Service` | Single-feature orchestration or business logic |
| `*Coordinator` | Multi-branch or multi-stage orchestration (reserved for genuinely complex fan-in/fan-out) |
| `*Handler` | Pipeline stage listener, reacts to one event type |
| `*Event` | Internal pipeline event |
| `*Repository` | Data access interface |
| `*GraphRepository` | Neo4j-specific repository (existing convention — preserve until standardization pass) |
| `*ReadRepository` | Query-only repository split (use when read/write separation is actively needed) |

Avoid introducing new `*OrchestrationService` names — use `*Coordinator` for fan-in cases and `*Service` for everything else.

### DTO placement rule

- DTOs consumed and produced only by one feature area live in that feature area.
- DTOs consumed by both `web` and `core` are shared contract candidates — they belong in a minimal shared area or, if a future `api` module exists, in that module.
- Prefer duplication over the wrong abstraction. Two nearly identical DTOs in two features is better than a shared DTO that creates a hidden coupling channel.
- Transport DTOs (HTTP request/response shapes) must not be used inside `application` or `domain` logic — pass domain primitives or dedicated application-layer types instead.

### Repository naming guidance

The codebase currently uses `*GraphRepository`, `*ReadRepository`, and `*WriteRepository` with no clear rule. Until a naming standardization pass is explicitly scoped, do not introduce additional naming patterns. New repositories should follow `*GraphRepository` as the default.

### Stage exit criteria

Each stage has binary pass/fail conditions. A stage is complete only when all criteria are met.

**Stage 0 exit criteria:**
- Every type in `support` is classified as: web-owned transport, core-owned domain or orchestration, shared contract, or ambiguous/deferred.
- Every search DTO is classified as: web-owned or core-owned.
- Error-response shaping types have a designated owner.
- No new types have been moved — this stage is audit-only.

**Stage 1 exit criteria:**
- `support` contains only types that are genuinely shared contracts with two or more real consumers.
- Search transport DTOs have moved to their owning feature area or a designated contracts location.
- No type move in this stage required changing more than two call sites.
- The `web` vs `core` module boundary is now representable as a package split with zero circular dependencies.

**Stage 2 exit criteria:**
- Maven modules `lorevault-web` and `lorevault-core` exist and compile independently.
- No circular dependency between modules (`mvn dependency:analyze` passes).
- `lorevault-web` has no compile-time dependency on `lorevault-core` infrastructure classes.
- All existing tests pass.
- Optional `api` module exists only if it has two or more genuine consumers.

**Stage 3 exit criteria:**
- `ingestion` package has internal subpackages matching the canonical vocabulary (at minimum: `application`, `infrastructure`, and event types separated from handlers).
- No production package contains more than 15 public types without intentional justification.
- Naming vocabulary is consistent within each module.

### Anti-patterns to avoid

- **Layer-first rewrite**: converting feature-first packages to a horizontal `service/`, `repository/`, `controller/` layout. This erases feature cohesion and is explicitly out of scope.
- **Premature `api` module**: creating a third module before two real consumers of the shared surface exist.
- **Transport DTO reuse in core**: passing HTTP request/response types directly into application or domain services.
- **Cosmetic moves before ownership cleanup**: moving files for browsability before the `web` vs `core` boundary is clear — this creates move-twice work.
- **`support` replaced by `shared` or `common`**: renaming the catch-all package without reducing its scope. The goal is a smaller shared area with intentional membership, not a rename.
- **`*OrchestrationService` proliferation**: naming every service with `Orchestration` when `*Service` or `*Coordinator` is sufficient.

## Success Criteria

- There is a clear, bounded proposal for how internal package structure should evolve while preserving the top-level feature split.
- There is a clear staged plan for sequencing package cleanup relative to later module extraction.
- Future reorganization work can identify high-value target packages and a sensible sequencing strategy.
- Contributors can apply a small consistent set of package semantics when adding new code.
- Shared packages such as `support` have a narrower and more intentional meaning.
- Large mixed-responsibility packages have an agreed direction for being split into more legible subareas.
- The repository avoids broad pre-split cleanup that would likely be repeated during module extraction.

## Links

- Related rules: `../rules/development-workflow.md`
- Related planning index: `README.md`
- Relevant source root: `../../lorevault-api/src/main/java/com/lorevault/api`
