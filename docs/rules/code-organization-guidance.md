# Code organization guidance

This rule defines the default package-organization guidance for production Java code in LoreVault.

The core rule is simple:

- top-level packages represent **product capabilities** or true cross-cutting foundations
- package placement should follow **semantic ownership**, not implementation technique
- prefer the **minimum structure that keeps ownership obvious**

## Top-level package semantics

Use the existing feature-oriented split under `com.lorevault.api`.

| Package | Semantic meaning |
|---|---|
| `ai` | generic interaction with LLM APIs and AI infrastructure |
| `config` | Spring bean wiring and framework configuration |
| `content` | the canonical persisted knowledge model and graph state of the product |
| `health` | monitoring, diagnostics, readiness, and observability |
| `ingestion` | everything related to turning uploads into `content` |
| `library` | canon scope, corpus boundaries, and library/catalog operations |
| `search` | navigating, querying, retrieving, and answering over `content` |
| `web` | HTTP and UI edges |

## Current content split

`content` is an internal semantic umbrella, not a flat bucket.

Use these content subareas:

| Package | Meaning |
|---|---|
| `content.library` | universe / series / book structures and publication-position value types |
| `content.entities` | materialized graph entities produced or enriched through ingestion |
| `content.timeline` | temporal understanding and ordering stored as part of content |

Interpretation:

- `content.library` defines corpus scope and publication structure
- `content.entities` holds the concrete graph entities the product operates on
- `content.timeline` is part of the content model, not a separate top-level feature

## Ownership rules

### Prefer semantic ownership over technical placement

- Put code in the package that gives it product meaning.
- Do **not** place code under `ai` just because it calls an LLM.
- Do **not** place code under `infrastructure` just because it feels technical.

Examples:

- generic prompt rendering belongs in `ai`
- a scene-detection workflow that is part of chapter processing belongs in `ingestion`
- persisted scene, chunk, mention, and temporal graph types belong in `content`

### `ai` is intentionally narrow

`ai` should contain generic AI-facing concerns such as:

- model clients
- prompt repositories and prompt rendering
- retry strategies and API-call support code
- reusable AI-facing contracts or parsing support when they are not feature-owned

If a type is orchestrating a product workflow such as ingestion, search, or another feature, it usually does **not** belong in `ai`.

### `ingestion` owns content-production workflows

`ingestion` should contain:

- upload-to-graph orchestration
- pipeline handlers and stage coordination
- extraction, reduction, and resolution flows
- feature-owned scene-processing logic used to build or enrich content

### `search` owns retrieval workflows

`search` should contain:

- semantic retrieval
- filtering and navigation over content
- query interpretation
- RAG and answer-generation orchestration

### `library` owns canon boundaries

`library` should contain:

- operations around universes, series, books, and corpus boundaries
- command/query services for canon scope management
- library-facing validation and rules that are not merely data storage concerns

## Default internal shape

A feature should stay flat by default.

Add internal subpackages only when the feature is both:

- large enough that browsing has become harder
- semantically mixed enough that the split reveals real ownership

These are available buckets, not a mandatory template:

| Subpackage | Use it for |
|---|---|
| `application` | orchestration, use-case flow, handlers, coordinators |
| `domain` | rules, policies, value objects, invariants, meaning-bearing models |
| `infrastructure` | repositories, adapters, external clients, persistence helpers |
| `events` | shared workflow events used across multiple emitters/listeners |

Use only the buckets a feature has actually earned.

## Web boundary rules

- `web` stops transport concerns at the edge.
- HTTP request and response DTOs belong in `web`.
- Do **not** pass HTTP request/response DTOs into `application`, `domain`, or `content` code.
- Prefer small feature-owned contracts or primitives over leaking transport shapes inward.

## Dependency direction

- `web` may depend on feature application code and edge DTOs.
- `application` may coordinate feature-owned domain/content/infrastructure code.
- `domain` should not import `web`.
- `domain` should avoid depending on infrastructure concerns.
- `events` should not depend on handlers, controllers, or other implementation details.
- Treat dependency-direction violations as boundary smells, not harmless style drift.

## Type placement guidance

### Handlers

- Handlers are application code.
- Event-driven pipeline handlers belong in `application` unless a denser split has clearly emerged.

### Events

- Events are workflow facts.
- Use `events` when they are shared across multiple emitters/listeners.
- Keep them beside a small orchestration cluster only when they are truly private glue.

### Exceptions

- Exceptions should live as close as possible to the code that gives them meaning.
- Create an `exceptions` package only when there is a dense, coherent exception cluster.

### Mappers

- Keep mapping code near the boundary that needs it.
- Web mapping belongs near `web`.
- persistence or adapter translation belongs near `infrastructure`.
- Do **not** create a generic `mappers` bucket by default.

### Config

- Keep configuration centralized unless a feature clearly owns meaningful bean wiring.
- Do **not** create per-feature `config` packages just to complete a template.

## DTO and shared-contract rules

- DTOs used by only one feature belong in that feature.
- Shared contracts should remain intentionally small.
- If a type does not have one clear owner, treat that as a design question before creating a shared bucket.
- Do **not** use `support`, `common`, or `shared` as convenience dumping grounds.

## Naming guidance

Name types for product semantics, not implementation technique.

- prefer names like `SceneRelationshipAnalysisService` over technique-leaking names like `TriadOrchestrationService`
- keep technique terms (for example, `Triad*`) for private/internal helpers where the technique itself is the meaning

Use these suffixes consistently:

| Suffix | Meaning |
|---|---|
| `*Controller` | HTTP entry point |
| `*Service` | feature-owned orchestration or business logic |
| `*Coordinator` | multi-branch or multi-stage orchestration |
| `*Handler` | pipeline-stage listener reacting to one event type |
| `*Event` | internal workflow event |
| `*Repository` | data-access interface |
| `*GraphRepository` | Neo4j-specific repository |
| `*ReadRepository` | query-only repository when read/write split is intentional |

Avoid introducing new `*OrchestrationService` names. Use `*Coordinator` for true fan-in/fan-out orchestration and `*Service` otherwise.

## Repository naming

The codebase currently mixes `*GraphRepository`, `*ReadRepository`, and `*WriteRepository`.

Until a dedicated naming-standardization pass is explicitly scoped:

- do not introduce new naming patterns
- use `*GraphRepository` as the default for new repositories

## Practical decision rule

When placement is ambiguous, ask these questions in order:

1. What product capability gives this type meaning?
2. Is this generic infrastructure, or feature-owned workflow logic?
3. Is this part of the persisted content model, or logic that produces/reads that model?
4. Does a new subpackage reveal real ownership, or just add ceremony?

Choose the smallest package structure that answers those questions clearly.

## Related docs

- [Development workflow](development-workflow.md)
- [Service design principles](service-design-principles.md)
