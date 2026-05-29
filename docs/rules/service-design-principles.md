# Service Design Principles

**Status:** Active

## Core Rules

- create services for business capabilities, not incidental helper logic
- extract narrow interfaces only for real external boundaries or tightly bounded ownership seams
- keep closely related workflow logic together
- prefer private methods over creating thin delegating services

## Smells To Avoid

- services with only a handful of pass-through methods
- internal validation or utility services with no independent business meaning
- multiple services that always change together and share the same dependencies
- service boundaries created only to preserve an old architecture style

## Practical Guidance

- if a user could describe the operation as a meaningful capability, a service may be justified
- if the logic only supports one larger workflow internally, keep it inside that workflow's service
- if the boundary is Neo4j, an LLM provider, another external system, or a tightly bounded ownership seam with clear semantic value, abstraction may be justified

## Abstraction Scope Must Match Domain Scope

The scope of a method signature must match the domain scope of the concept it models. A book-scoped concept must not receive a chapter-scoped API just because chapters are convenient input units.

**Example:** `buildTriadsForChapter(Chapter)` models triads as a per-chapter concept, but triads are sliding windows over all scenes in a book. Chapter boundaries are an ingestion convenience, not a domain property. The mismatch forces asymmetric helpers (`resolveCrossChapterPreviousScene` with no matching next resolution), nulls where the domain has data (chapter-last scenes always have `next = null`), and callers working around the API to reach across chapter boundaries.

**Fix:** `buildTriad(UUID sceneId)` — the scope matches the domain. Callers iterate chapter scenes as an implementation detail, not a signature constraint.

**Heuristic:** If a method needs asymmetric cross-boundary helpers to model its domain correctly, the boundary is wrong.

## StageExecutionContext Threading

Services that create or persist domain nodes must accept `StageExecutionContext ctx` as their first parameter. This context carries `(stageId, jobId, chapterId, bookId, stage)` and flows explicitly from the handler through the service to the repository.

```java
// Required — ctx threaded through
public ChapterIndividualConsolidationResult consolidateChapter(
        StageExecutionContext ctx, UUID chapterId) {
    // ...
    chapterIndividuals.add(new ChapterIndividual(
            UUID.randomUUID(), chapterId, ctx.stageId(), ...));
}

// Wrong — ctx available in handler but not passed to service
public ChapterIndividualConsolidationResult consolidateChapter(UUID chapterId) {
    // no stageId on created entities — cleanup and replay will fail
}
```

### Why explicit threading, not ThreadLocal?

`StageExecutionContext` is an explicit method parameter, not a `ThreadLocal`. This makes the dependency visible in the method signature, testable without thread setup, and safe across async boundaries. See ADR-014.

### Why `ctx` as the first parameter?

Placing `StageExecutionContext ctx` as the first parameter makes it easy to spot missing threading during code review. If a service method creates domain nodes but doesn't have `ctx` as its first parameter, it's missing provenance.

### Handler → Service → Repository flow

```
StageDispatcher.dispatch(event)
  → handler.execute(ctx)
    → service.consolidateChapter(ctx, chapterId)
      → new ChapterIndividual(UUID.randomUUID(), chapterId, ctx.stageId(), ...)
        → repository.save(entity)
```

The `stageId` ends up as a `@Property("stageId")` on the Neo4j node, enabling `deleteDataByStageId(stageId)` for cleanup and replay.

---

## Additional Heuristics

### Good signs

- a service owns one meaningful business capability
- the public methods form a cohesive workflow surface
- related logic stays together instead of hopping across helper services

### Warning signs of over-segmentation

- a service has only 1-3 pass-through methods
- multiple services are always called together
- several services share the same dependencies and change together
- method names just repeat the service name with no stronger concept

### Warning signs of under-segmentation

- one service handles multiple unrelated business capabilities
- one service directly coordinates several external systems with little cohesion
- the public surface has grown too large to understand as one capability

## Implementation Guidance

When implementing a feature:

1. start with one service that handles the whole business operation
2. split only when you encounter a real external boundary
3. prefer private methods for internal workflow steps
4. ask whether the service name describes a complete user-meaningful capability

When consolidating existing code:

1. identify service clusters that always work together
2. merge them into the primary business workflow service
3. convert thin helpers back into private methods when appropriate
4. keep only boundary abstractions that represent real external systems or clearly justified ownership seams

## Testing Implication

Prefer tests that exercise the full business operation at a meaningful boundary rather than preserving artificial service splits.

## Why This Rule Exists

LoreVault already paid the cost of over-segmentation. These rules exist to stop the codebase from drifting back toward thin wrappers, fake boundaries, and choreographed internal service hops.
