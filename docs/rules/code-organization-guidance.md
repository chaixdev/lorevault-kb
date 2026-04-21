# Code organization guidance

This rule records the default package-organization guidance for production Java code in LoreVault.

Use it when placing new code, deciding whether a package should stay flat, and evaluating whether a package should gain internal subpackages.

## Preserve the top-level feature split

- Keep the existing feature-oriented top-level packages under `com.lorevault.api` by default.
- Do **not** convert the codebase into a layer-first `service/`, `repository/`, `controller/` layout.
- Prefer moving code closer to its owning feature over introducing new catch-all shared areas.

## Canonical subpackage vocabulary

When a feature package grows beyond roughly 10–15 public types, or when it clearly mixes transport concerns with domain or infrastructure concerns, use this vocabulary:

| Subpackage | What belongs there |
|---|---|
| `web.command` | HTTP command controllers, request models, response shaping, validation, file extraction |
| `web.query` | HTTP query controllers, read-path request and response models |
| `web.ui` | Server-side UI controllers, Thymeleaf view models, form objects |
| `application` | Orchestration services, coordinators, pipeline handlers, use-case services |
| `domain` | Core domain models, domain-specific rules, value objects |
| `infrastructure` | Repository implementations, graph clients, AI clients, external service adapters |

The existing `web.command`, `web.query`, and `web.ui` split already matches this vocabulary and should be preserved and strengthened rather than replaced.

## Dependency direction

Dependencies must flow in one direction only:

```text
web → application → domain
infrastructure → domain
```

- `web` must not import from `infrastructure` directly.
- `domain` must not import from `web` or `infrastructure`.
- Treat violations as boundary smells, not as harmless style drift.

## Type ownership

- Every type must have a single owning feature package.
- If a type cannot be assigned to one feature, treat it as a shared-contract candidate.
- Do **not** use `support` as a catch-all for unclear ownership.
- Types in `support` should survive a strict question: do at least two distinct features genuinely need this, or is this only internal coupling disguised as sharing?

## Naming vocabulary

Use these suffixes consistently:

| Suffix | Meaning |
|---|---|
| `*Controller` | HTTP entry point, command or query |
| `*Service` | Single-feature orchestration or business logic |
| `*Coordinator` | Multi-branch or multi-stage orchestration |
| `*Handler` | Pipeline stage listener, reacts to one event type |
| `*Event` | Internal pipeline event |
| `*Repository` | Data access interface |
| `*GraphRepository` | Neo4j-specific repository |
| `*ReadRepository` | Query-only repository split when read/write separation is actively needed |

Avoid introducing new `*OrchestrationService` names. Use `*Coordinator` for genuine fan-in/fan-out cases and `*Service` otherwise.

## DTO placement

- DTOs used by only one feature belong in that feature.
- DTOs used by both `web` and `core` are shared-contract candidates.
- Prefer small duplication over the wrong shared abstraction.
- Do **not** pass HTTP request/response DTOs into `application` or `domain` logic.

## Repository naming

The codebase currently mixes `*GraphRepository`, `*ReadRepository`, and `*WriteRepository`.

Until a dedicated naming-standardization pass is explicitly scoped:

- do not introduce new naming patterns
- use `*GraphRepository` as the default for new repositories

## Stage guidance

- Perform ownership cleanup before cosmetic package reshuffling.
- Preserve already-cohesive areas instead of forcing extra subpackages.
- Only split packages where a real navigability or ownership problem exists.
- Defer ambiguous cases until feature work makes the right home obvious.

## Failure modes this rule is meant to prevent

### Shared packages become dumping grounds

Once a package like `support` is treated as a convenient neutral zone, it tends to accumulate types with no clear owner. That feels fast in the short term, but it hides feature ownership and makes later cleanup expensive.

Lesson: if a type has a real owning feature, put it there. Shared space should stay intentionally small.

### Transport contracts leak into core logic

Reusing HTTP request/response DTOs inside application or domain code usually looks efficient at first. In practice, it couples business logic to transport concerns, makes later API changes more dangerous, and blocks clean module boundaries.

Lesson: transport shapes should stop at the web boundary. Core code should depend on domain primitives or application-layer contracts instead.

### Cosmetic package cleanup creates move-twice churn

Reorganizing for browsability before ownership is clear often means the same files move again when feature seams or module seams become better understood.

Lesson: do ownership cleanup first, cosmetic cleanup second.

### Layer-first rewrites destroy feature cohesion

Splitting everything into horizontal `service`, `repository`, and `controller` packages can look neat on day one, but it usually makes real feature flows harder to trace. Related code stops living together.

Lesson: preserve feature-first organization unless there is a very strong reason not to.

### Premature shared-contract extraction freezes the wrong abstractions

Creating an `api`, `shared`, or `common` area too early often turns temporary coupling into a permanent public surface. Teams then have to preserve the wrong contracts because too many call sites depend on them.

Lesson: do not promote a type into a shared contract until there are real multiple consumers and the boundary has stabilized.

### Naming inflation hides simple responsibilities

Once names like `*OrchestrationService` start spreading, they often become a vague label for “does a lot of things.” That makes boundaries harder to reason about, not easier.

Lesson: prefer simple, constrained naming. Use `*Service` by default and reserve `*Coordinator` for true multi-branch orchestration.

## Related docs

- [Development workflow](development-workflow.md)
- [Service design principles](service-design-principles.md)
- [Planning: staged package reorganization and module split prep](../planning/staged-package-reorganization-and-module-split-prep.md)
