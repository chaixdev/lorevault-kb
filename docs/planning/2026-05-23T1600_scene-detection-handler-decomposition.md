# Scene Detection Handler Decomposition

**Date:** May 23, 2026
**Status:** Parked — potential future task, not part of immediate cleanup
**Discovered:** Code walkthrough post durable-ingestion-orchestration implementation

## Problem

`SceneDetectionHandler.execute()` is a 300+ line method coordinating 14 injected services. After removing 5 orchestration fields (planned via dispatcher in `submission-flow-cleanup.md`), 14 domain dependencies remain:

| # | Service | Role |
|---|---------|------|
| 1 | `ChapterGraphRepository` | Fetch chapter |
| 2 | `SceneGraphRepository` | Check existing scenes |
| 3 | `SceneDetectionService` | LLM: detect scene boundaries |
| 4 | `SceneProcessingService` | Persist detected scenes |
| 5 | `IndividualPersistenceService` | Persist individual entities |
| 6 | `CollectivePersistenceService` | Persist collective entities |
| 7 | `ObjectPersistenceService` | Persist object entities |
| 8 | `LocationPersistenceService` | Persist location entities |
| 9 | `EventPersistenceService` | Persist event entities |
| 10 | `RelationClaimPersistenceService` | Persist relation claims |
| 11 | `DefaultTemporalEdgeService` | Create default temporal edges |
| 12 | `SceneTemporalRelationshipPersistenceService` | Apply temporal relationships |
| 13 | `TriadTemporalEdgeRequestFactory` | Build triad edge requests |
| 14 | `SceneRelationshipAnalysisService` | Analyze scene relationships |

All 14 are genuinely used. None are trivial one-liner wrappers — each persistence service has 80-120 lines of mapping and transformation logic. Scene detection is a 14-step pipeline that currently lives in a single `execute()` method.

## Options

### Option A: Extract sub-orchestrators

Split `execute()` into logical groups, each with its own orchestrator:

```
SceneDetectionHandler
  ├── SceneSegmentationOrchestrator (uses #3, #4, #1)
  ├── TemporalEdgeOrchestrator (uses #11, #12, #13, #14)
  └── EntityPersistenceOrchestrator (uses #5-10)
```

Handler becomes ~3 dependencies, each orchestrator has 3-6. Total dependency count unchanged, but each class has a clear responsibility and `execute()` becomes a 3-step sequence.

**Tradeoff:** Moves complexity around. Adds 3 new classes. Makes testing more granular (can test persistence without LLM calls).

### Option B: Pipeline pattern

Sequence of `Stage` objects, each with its own injected dependencies. Handler iterates stages:

```java
private final List<PipelineStep> steps;  // auto-wired

public StepResult execute(UUID jobId, UUID chapterId) {
    PipelineContext ctx = new PipelineContext(jobId, chapterId);
    for (PipelineStep step : steps) {
        step.execute(ctx);
    }
    return ctx.result();
}
```

Each `PipelineStep` is a self-contained bean with 1-3 dependencies.

**Tradeoff:** Most composable, but adds abstraction overhead for a single handler.

### Option C: Leave as-is

14 dependencies for a 14-step pipeline is not unreasonable. The class is a facade — it orchestrates a complex workflow. Every dependency serves a distinct step. No single service handles 2 tasks.

**Tradeoff:** Accepts the injection count as domain complexity cost. Refactoring to sub-orchestrators doesn't reduce total dependency count — it just re-shapes the class hierarchy.

## Recommendation

Hold. The dispatcher (submission-flow-cleanup #7) removes the 5 orchestration fields, leaving 14 domain dependencies. That's the current state. Decomposing `execute()` is a separate effort with its own tradeoffs — worth doing when scene detection grows another step or when testing becomes painful. Not urgent for wipe-state dev.

## Files Affected

- `lorevault-core/src/main/java/com/lorevault/api/ingestion/scene/SceneDetectionHandler.java` — primary target
- 3-5 new classes if Option A or B is chosen

## Estimated Effort

~2-3 hours depending on approach. Higher risk than cleanup items — changes the behavior boundary.
