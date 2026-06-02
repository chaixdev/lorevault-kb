# Service-Oriented vs Rich Domain Model

**Date:** May 24, 2026
**Type:** Design observation — not prescriptive, not an action item
**Discovered:** Code walkthrough post durable-ingestion-orchestration implementation

## The tension

This codebase was "vibe coded" — LLMs generated most of it. The LLM default is a service-oriented style: records as data bags, `@Service` classes as stateless function collections, intermediate result records for every return value. The domain (books, chapters, scenes, temporal edges) is naturally relational and object-oriented, but the code expresses it through pipelines and service orchestration.

This document captures the paradigm tension so future design decisions can be made consciously rather than by LLM default.

## Concrete contrast

### What the codebase does (service-oriented)

```java
// Objects are data bags. Services do the thinking.
Chapter chapter = chapterRepo.findById(chapterId);
List<Scene> scenes = sceneRepo.findByChapterId(chapterId);

// External builder figures out scene adjacency
List<SceneTriad> triads = triadBuilder.buildTriadsForChapter(chapter);

// External service analyzes them
SceneRelationshipOutcome outcome = analysisService.analyzeChapterTriads(jobId, chapter);
```

Behavior lives in services. Objects have getters and setters (Lombok `@Data`/`@Getter`/`@Setter`). To understand what you can do with a Scene, trace through services that accept Scene parameters.

### What richer domain objects would do

```java
// Chapter knows how to build its triads
List<SceneTriad> triads = chapter.triads();

// Scene knows its neighbors
Scene next = scene.next();

// Temporal inference lives with the entity pair
TemporalEdge edge = scene.inferEdgeTo(next);
```

Behavior lives with data. To understand what a Scene can do, read `Scene.java`.

### Trade-offs

| Dimension | Service-oriented | Rich domain model |
|-----------|-----------------|-------------------|
| **Discoverability** | Trace service → service to find operations | Read the domain class |
| **Testability** | Mock repositories, test services in isolation | Need graph state or in-memory domain graph |
| **Parallelism** | Stateless services — trivial to parallelize | Mutable state needs careful design |
| **Change isolation** | Add a resolution step without touching Chapter/Scene | Adding behavior touches the domain class |
| **Invariant enforcement** | Must be checked at service boundaries | Can live on the domain object |
| **LLM generation** | Natural pattern match (record + service = familiar) | Harder for LLMs — requires explicit design |
| **Pipeline fit** | Ingestion IS a pipeline — natural fit | Pipeline stages still exist, domain objects are richer |

## Record proliferation

Records make type creation free. In JDK8, creating a class to bundle 4 return values required ~40 lines of ceremony. With records, it's 1 line. The friction inverted, and types proliferate.

### When a record earns its existence

**Payload bundles** — heterogeneous typed lists that travel together through the domain:

```java
// 7 typed lists, consumed in multiple places, carries domain data
public record SceneRelationshipOutcome(
    List<SceneRelationshipAnalysis> triadAnalyses,
    List<SceneIndividualExtraction> sceneIndividualExtractions,
    // ... 5 more entity types
) {}
```

### When a record is scaffolding

**Counting results** — identical shapes with different type tags, consumed once to build a `StageResult`:

```java
// All 9 share the exact same fields. Different names, no different behavior.
public record ChapterIndividualConsolidationResult(UUID id, boolean success, int processed, int created, String message) {}
public record BookObjectConsolidationResult(UUID bookId, boolean success, int processed, int created, String message) {}
// ... 7 more
```

The type tag (`Individual`, `Object`) carries information the caller already knows (it called `individualConsolidationService`, not `objectConsolidationService`). The record exists solely to carry two integers 3 lines up the call stack before being converted to `StageResult`.

### Design question

If the handler only exists to convert `ConsolidationResult` → `StageResult`, should the service return `StageResult` directly? This eliminates the intermediate type and the handler's repackaging boilerplate in one move. Cost: the service knows about a pipeline type (`StageResult`).

## Intermediate result types vs DTOs

Traditional DTOs cross boundaries: API layer → persistence, service → HTTP response. The intermediate result records in this codebase cross nothing — they're return-value glue between adjacent methods in the same package. They exist because records made them cheap, not because the boundary justified a type.

The older alternative (mutable context objects passed through methods) wasn't better — it coupled all participants to a shared object and made parallel execution difficult. But it reflected a design instinct worth preserving: if the return value is just scaffolding for the next method call, the boundary between those methods may be wrong.

## This is not a call to action

The codebase works. Ingestion IS a pipeline, and the service-oriented style maps to the problem. LLM-driven development will continue to default to this style. This document exists so that:

1. Future design decisions are conscious, not accidental
2. When refactoring, you can ask: "is this a `SceneRelationshipOutcome` (earns its type) or a `ChapterIndividualConsolidationResult` (scaffolding)?"
3. The walkthrough doesn't need to re-derive this tension every time it surfaces

## Related

- `docs/archive/planning/2026-05-23T1530_submission-flow-cleanup.md` — #13 (guard duplication), #14 (triad chapter abstraction), #16 (extraction loop collapse) all touch on where behavior should live; #21 (eliminate unnecessary intermediate result records) is a direct action on this pattern
- `docs/patterns/cross-cutting/dependency-inversion-seam.md` — architectural seam pattern already in use
- `docs/concepts/entity-claim-model.md` — the domain model this paradigm is expressing
