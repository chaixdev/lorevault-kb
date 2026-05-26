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

## Current package map

LoreVault keeps the top-level feature split under `com.lorevault.api`, then uses
capability-oriented internal packages where a feature is dense enough to need them.

Representative current internal shape:

| Top-level package | Representative internal packages |
|---|---|
| `ai` | `chunking`, `embedding`, `llm`, `infrastructure` |
| `content` | `association`, `chapter`, `chunk`, `mention`, `scene`, `timeline` |
| `ingestion` | `completion`, `content`, `events`, `job`, `pipeline`, `resolution`, `scene`, `submission`, `triad`, `infrastructure` |
| `library` | `book`, `series`, `service`, `universe` |
| `search` | `extraction`, `model`, `rag`, `semantic` |

This map is descriptive, not a mandatory template. The rule is capability ownership first,
with local support packages only where they remain semantically honest.

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

A feature should stay flat until it becomes semantically mixed enough to justify a split.
When it does need internal structure, default to capability-oriented subpackages rather than
recreating `application/domain/infrastructure` buckets by habit.

Prefer packages that communicate what the feature does:

- `scene`, `chunk`, `mention`, `association`
- `submission`, `job`, `resolution`, `completion`
- `rag`, `semantic`, `extraction`, `model`
- `book`, `series`, `universe`

Local support buckets are still valid when they are the clearest fit for one bounded area:

| Subpackage | Use it for |
|---|---|
| `events` | shared workflow events used across multiple emitters/listeners |
| `infrastructure` | technical support types that are genuinely shared within one feature |
| `pipeline` | small feature-local pipeline support that is not itself a business capability |
| `service` | a compact service/query seam when a feature is too small to justify finer capability splits |

Do not introduce `application`, `domain`, or `infrastructure` as a feature-wide template just
to complete a familiar architecture pattern. Use them only as a narrow local fit when the
semantics are still obvious and they do not become mixed catch-all buckets.

## Web boundary rules

- `web` stops transport concerns at the edge.
- HTTP request and response DTOs belong in `web`.
- Do **not** pass HTTP request/response DTOs into core feature packages or `content` code.
- Prefer small feature-owned contracts or primitives over leaking transport shapes inward.

## Dependency direction

- `web` may depend on `lorevault-core` feature packages and edge DTOs.
- Core feature packages must not import `web`.
- Shared support packages inside a feature (`events`, `model`, `pipeline`, `infrastructure`) should not depend on controllers or transport DTOs.
- `events` should not depend on handlers, controllers, or other implementation details.
- Treat dependency-direction violations as boundary smells, not harmless style drift.

## Type placement guidance

### Handlers

- Handlers are workflow-entry types.
- Put them beside the capability or pipeline stage they belong to, not in a generic `application` bucket by default.

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

## Container-class guidance

For LLM response types and other closed sets that always travel together, use a `public final class` with a private constructor as a namespace:

```java
public final class TriadAnalysisModels {
    private TriadAnalysisModels() {}
    
    public record SceneRelationshipAnalysis(...) {}
    public record IndividualExtraction(...) {}
    // ... rest of the closed set
}
```

Apply this pattern when:

- The types form a closed set that always travels together (e.g., LLM deserialization targets)
- Each type is < 20 lines and too thin to justify a separate file
- The types are only ever referenced through the container (no external direct usage)

Prefer `*Models` suffix for container classes that group LLM deserialization targets (`TriadAnalysisModels`, `EventCorefModels`, `EventMergeModels`).

Otherwise, use separate top-level records in the same package. Do not use container classes as default grouping just because types are small — the closed-set criterion is the gate.

---

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

## Backward compatibility is never the goal

**Deprecated code does not exist in this codebase.** Code is either used and must remain, or unused and must be deleted. There is no third state. `@Deprecated` as a Java language feature is not used here — it is not a signal, a preference, or a migration tracker.

If a method, enum value, or class has zero production callers, delete it. If it still has callers, migrate them to the replacement and then delete it. Do not add `@Deprecated` to keep old code alive, and do not add it to "acknowledge" that something exists but is less preferred. Use an inline comment if you need to convey design intent about a value's role.

"Backward compatibility" in Javadoc is a deletion signal. Any method claiming to "exist only for backward compatibility" must be deleted immediately.

When you are tempted to keep old code:
1. Who calls it? If zero production callers → delete.
2. If callers exist, what is the replacement? Migrate callers → delete.
3. If no replacement exists yet, file a migration task. Do not add `@Deprecated`.

Direct deletion with caller migration is always preferred over any form of deprecation.

## Related docs

- [Development workflow](development-workflow.md)
- [Service design principles](service-design-principles.md)
