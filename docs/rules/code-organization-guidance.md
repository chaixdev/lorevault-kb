# Code organization guidance

This rule records the default package-organization guidance for production Java code in LoreVault.

Use it when placing new code, deciding whether a package should stay flat, and evaluating whether a package should gain internal subpackages.

## Preserve the top-level feature split

- Keep the existing feature-oriented top-level packages under `com.lorevault.api` by default.
- Do **not** convert the codebase into a layer-first `service/`, `repository/`, `controller/` layout.
- Prefer moving code closer to its owning feature over introducing new catch-all shared areas.

## Default shape: flat features first

- A feature package should stay flat by default.
- Add internal subpackages only when the feature is both **large enough** and **semantically mixed enough** that flat placement has become harder to browse or reason about.
- Do **not** treat package structure as a mandatory four-bucket template.
- Treat subpackage names as a small vocabulary that a feature may use when it has earned them.

## Package vocabulary

### Edge vocabulary already established in LoreVault

| Subpackage | What belongs there |
|---|---|
| `web.command` | HTTP command controllers, command-side request models, validation, response shaping, file extraction |
| `web.query` | HTTP query controllers, read-path request and response models |
| `web.ui` | Server-side UI controllers, Thymeleaf form objects, view models |

The existing `web.command`, `web.query`, and `web.ui` split already matches the desired edge shape and should be preserved and strengthened rather than replaced.

### Internal feature vocabulary available when needed

| Subpackage | Use it for | Do not use it for |
|---|---|---|
| `application` | orchestration services, coordinators, handlers, workflow logic, use-case execution | dumping all business logic into service classes by default |
| `domain` | meaning-bearing models, rules, policies, value objects, invariants | generic POJO storage, DTO buckets, persistence-shaped records by convenience |
| `infrastructure` | repositories, graph adapters, external clients, persistence helpers, framework-heavy technical adapters | all code that merely feels “technical” without a real adapter role |
| `events` | shared workflow event types used across multiple emitters/listeners inside a feature | every incidental callback object or private one-off helper message |

### Important constraint

- These names are **available buckets**, not a required per-feature template.
- A feature may use none of them, some of them, or several of them.
- The right target is the **minimum structure that makes the feature legible**.

## Placement rules for commonly debated types

### Handlers

- Handlers are application code.
- If a handler reacts to an event, coordinates services, updates state, or emits follow-up events, it belongs in `application`.
- Do **not** create a separate `handlers` package unless the handler population becomes large enough that the split materially improves navigation.

### Events

- Events are usually **application-level workflow facts**, but they may still deserve a sibling `events` package.
- Keep events in `events` when they are shared across multiple emitters/listeners, used by support code, or imported outside a narrow orchestration cluster.
- Put events under `application` only when they are truly private workflow glue that changes in lockstep with one small implementation area.
- Event payloads should stay contract-like. If an event starts carrying handler-specific convenience state, treat that as a design smell.

### Exceptions

- Exceptions should live as close as possible to the feature area that gives them meaning.
- Prefer co-locating an exception beside the service, policy, or model that throws it.
- Introduce an `exceptions` subpackage only when a feature has enough related exception types that the grouping itself adds clarity.

### Mappers

- Keep mapping code close to the boundary that needs it.
- Web mapping belongs near `web`.
- Persistence or external-adapter translation belongs near `infrastructure`.
- Do **not** create a general `mappers` package unless there is a dense, coherent mapping cluster that earns the split.

### Config

- Keep configuration centralized by default.
- Introduce feature-local configuration only when a feature has meaningful, non-trivial configuration or bean wiring that is clearly owned by that feature.
- Do **not** create per-feature `config` packages just to complete a template.

## Dependency guidance

- `web` should stop transport concerns at the edge.
- `application` may coordinate `domain`, `infrastructure`, and feature-owned `events`.
- `domain` must not import `web`.
- `domain` should avoid depending on `infrastructure`.
- `events` should not depend on handlers, controllers, or other application implementation details.
- Treat dependency-direction violations as boundary smells, not as harmless style drift.

## Type ownership

- Every type must have a single owning feature package.
- If a type cannot be assigned to one feature, treat it as a shared-contract candidate.
- Do **not** use `support` as a catch-all for unclear ownership.
- Shared space should trend toward a minimal contract area, not toward a long-term convenience bucket.

## DTO placement

- DTOs used by only one feature belong in that feature.
- HTTP request and response DTOs should stop at the web boundary.
- Cross-boundary contracts should stay intentionally small and should exist only when there are real multiple consumers.
- Prefer small duplication over the wrong shared abstraction.
- Do **not** pass HTTP request/response DTOs into `application` or `domain` logic.

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
- Prefer updating the rule vocabulary first, then applying it selectively to the features that have actually earned more structure.

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

### Universal templates create fake structure

Forcing every feature into `application/domain/infrastructure/events` or into `service/repository/models/config/exceptions` creates the appearance of discipline without necessarily improving clarity.

Lesson: use the minimum structure that truthfully reflects the feature's internal shape.

### Neutral buckets hide meaning

Packages like `models`, `mappers`, `exceptions`, or `config` often become junk drawers when created by default rather than earned by real density.

Lesson: co-locate these concerns by default and split them out only when the grouping itself adds meaning.

### Premature shared-contract extraction freezes the wrong abstractions

Creating an `api`, `shared`, or `common` area too early often turns temporary coupling into a permanent public surface. Teams then have to preserve the wrong contracts because too many call sites depend on them.

Lesson: do not promote a type into a shared contract until there are real multiple consumers and the boundary has stabilized.

### Naming inflation hides simple responsibilities

Once names like `*OrchestrationService` start spreading, they often become a vague label for “does a lot of things.” That makes boundaries harder to reason about, not easier.

Lesson: prefer simple, constrained naming. Use `*Service` by default and reserve `*Coordinator` for true multi-branch orchestration.

## Related docs

- [Development workflow](development-workflow.md)
- [Service design principles](service-design-principles.md)
