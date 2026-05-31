# Ingestion Concurrency Model

**Status:** Established

## Purpose

This document describes the runtime concurrency model for LoreVault's ingestion pipeline: what can run in parallel, what ordering guarantees do and do not exist, and what risks arise when chapters for the same book are submitted concurrently. It is the authoritative reference for the concurrency behavior documented in code comments and the ingestion pipeline pattern.

## Current Threading Model

The pipeline runs on two named Spring executors:

| Executor | Core threads | Max threads | Queue | Used for |
|---|---|---|---|---|
| `sceneDetectionTaskExecutor` | 1 | 3 | 10 | Scene detection stage (`SceneDetectionHandler`) |
| `ingestionLaneTaskExecutor` | 1 | 1 | unbounded | All other pipeline stages |

Both executors are configured in `AsyncConfig`. The `sceneDetectionTaskExecutor` is the first point of concurrency and is bounded to discourage careless parallel chapter submissions. The single-threaded `ingestionLaneTaskExecutor` serializes all downstream branches per submission order.

## What Runs In Parallel — And What Does Not

### Same-chapter pipeline (serial by design)

All work for a single `(jobId, chapterId)` runs on `ingestionLaneTaskExecutor` after the scene detection stage completes. Within one chapter, the fan-out branches (`ChapterIndividualConsolidationHandler`, `ChapterLocationConsolidationHandler`, `ChapterObjectConsolidationHandler`, `ChapterCollectiveConsolidationHandler`, `ChapterEventConsolidationHandler`) are routed by `StageDispatcher` to `ingestionLaneTaskExecutor` and run concurrently with each other — and with `ChunkingHandler` and `EmbeddingHandler` — after the publishing transaction commits.

### Cross-chapter submissions (concurrent-capable)

Because `sceneDetectionTaskExecutor` allows up to 3 concurrent scene detection tasks, it is possible to submit multiple chapters for the same book simultaneously. `IngestionService.submitChapter()` publishes `ChapterIngestionEvent` asynchronously, and each event drives its own pipeline. Two chapters of the same book can therefore be in scene detection at the same time.

### Cross-chapter ordering guarantee: none

**The pipeline does not guarantee that canonically earlier chapters finish before later chapters.** If chapter N+1 is submitted after chapter N but the N+1 event wins a race slot, N+1 can reach scene detection, triad analysis, and `NEXT_IN_READING_ORDER` edge creation before N does.

## NEXT_IN_READING_ORDER: Idempotent Creation and Late Boundary Repair

`DefaultTemporalEdgeService.createAllDefaults(bookId)` runs as part of each chapter's scene detection pipeline. It performs two MERGE operations:

1. **In-chapter edges** — connect consecutive scenes within the same chapter. These are always created during the same chapter's pipeline run.
2. **Cross-chapter edges** — connect the last scene of chapter N to the first scene of chapter N+1. These are created when chapter N+1 runs.

The MERGE-based approach is idempotent: running it twice produces the same graph state as running it once. When chapter N+1 runs before chapter N, the cross-chapter edge is absent at first. When chapter N eventually runs, the MERGE finds the last scene of N and first scene of N+1 and creates `NEXT_IN_READING_ORDER`.

### Late boundary repair: temporal projection replay

The original pipeline ran triad analysis only during the current chapter's pipeline. When chapter N+1 processed before chapter N, triad analysis for N+1 had no earlier chapter scene to pair with, so no temporal relationship was inferred between the boundary scenes.

**The fix:** `createAllDefaults` now returns `DefaultTemporalEdgeCreationResult`, which includes the list of newly created cross-chapter boundaries. `SceneDetectionHandler` uses that list to detect which boundaries were missing and replays boundary temporal projection narrow and idempotently:

- For each new boundary, it checks `sceneTemporalRelationshipPersistenceService.hasAnyTemporalRelationshipBetween(previousSceneId, nextSceneId)` in both directions.
- If a `TEMPORAL` relationship already exists in either direction, replay is skipped.
- Otherwise, it runs `analyzeChapterTriads` for the next chapter with `triadChapter = boundary.getNextChapterId()` and filters the resulting triad analyses to the boundary pair, then writes the `TEMPORAL` edge directly without calling the LLM again.

This means:
- **Happy path** (chapters submitted in order): triad analysis runs once per chapter, no replay overhead.
- **Out-of-order submission**: when a late `NEXT_IN_READING_ORDER` edge appears, the boundary temporal relationship is repaired the next time any chapter of the same book runs through scene detection.
- **Idempotent replay guard**: an already-present `TEMPORAL` edge (regardless of direction) prevents redundant LLM calls.

## Concurrent Submission Admission: QUEUED State Block

When a second batch of chapters is submitted before the first batch finishes, those jobs are created with status `QUEUED` and their events are published. `SceneDetectionHandler` is the first listener to move a job out of `QUEUED`. With `sceneDetectionTaskExecutor` set to core=1, max=3, queue=10, the executor's queue can absorb some burst, but there is no explicit gate preventing a second concurrent batch from competing for slots alongside an unfinished first batch.

**Known issue:** if concurrent submissions for the same book are queued faster than the executor can drain them, or if the executor is already saturated with other books' chapters, submitted jobs can remain in `QUEUED` indefinitely without a dedicated retry trigger.

**Current mitigation:** no explicit admission guard exists for concurrent same-book submissions. The existing executor bounds provide soft backpressure only.

**Follow-up:** if strict ordering or per-book admission is needed, add a durable gate that rejects or defers submissions when a prior job for the same book is not yet terminal (completed, failed, or cancelled). Until then, applications uploading chapters concurrently for the same book may observe the ordering/replay behavior described above.

## What Is Not Supported Yet

| Behavior | Current state | Notes |
|---|---|---|
| Per-book submission serialization | Not implemented | Chapters for the same book can race. Boundary replay handles the symptom (missing `TEMPORAL` edges) but does not prevent the race. |
| Concurrent batch admission control | Not implemented | Second batch submissions while first is active may cause QUEUED jobs to stall if executor is saturated. |
| Intra-chapter triad parallelization | Not implemented | `SceneRelationshipAnalysisService` processes triads sequentially within a chapter. Parallelizing triads is feasible (bounded executor, result ordering by `triadIndex`) but should wait until the queue/admission model is explicit, because compounding chapter-level and intra-chapter concurrency adds debugging surface. |

### Claim-Based Concurrency for Book-Level Stages

Book-level consolidation stages (`BookIndividual`, `BookLocation`, `BookObject`, `BookCollective`, `BookEvent`) use `BookConsolidationClaimService` to prevent concurrent reduction of the same book. The claim is a Neo4j MERGE-based lock: `tryAcquireClaim(bookId, lane, stageId)` atomically creates a `BookConsolidationClaim` node. If the claim already exists, the handler returns `StageResult.retryableFailure()` and the stage is retried later.

This claim-based approach is necessary because book-level consolidation is destructive (delete-and-rebuild) and must not run concurrently for the same book. The `stageId` on the claim node provides provenance for cleanup.

## Relationship to Other Patterns

- **Ingestion Pipeline Pattern** — This document is a child of the pipeline pattern. It extends the "Concurrency and Timing View" section with explicit ordering guarantees and known gaps.
- **Handler Retry-Safety Pattern** — The concurrency model here is consistent with retry-safety: handlers own their projection scope, and out-of-order replay does not invalidate owned output — it only repairs missing boundary edges.
- **Triad Analysis Pattern** — Triad analysis provides the LLM classification for temporal edges. This document describes when triad analysis runs and how the boundary replay interacts with it.
- **ADR-013** — Documents the `StageDispatcher` architectural decision, including the rationale for lane-based executor routing and the claim-based concurrency model for book-level consolidation stages.

## Design Constraints and Rationale

1. **No global chapter queue.** The pipeline uses `StageDispatcher` with Spring `@EventListener` for event routing and named executors for thread management. Adding a global queue with per-book ordering would introduce a scheduling layer that the current dispatcher model deliberately avoids.

2. **Single-threaded downstream executor.** `ingestionLaneTaskExecutor` is single-threaded to serialize downstream branch work and avoid write contention between concurrent submissions. `StageDispatcher` routes `SCENE_SEGMENTATION` to `sceneDetectionTaskExecutor` and all other stages to `ingestionLaneTaskExecutor`. This is a conservative choice; fan-out concurrency happens at the dispatcher level, not at the executor level.

3. **Idempotent default edges over locking.** `NEXT_IN_READING_ORDER` uses `MERGE` rather than pessimistic locking. This trades some retry overhead (a second `MERGE` that finds an existing edge) for the ability to run without coordination.

4. **Boundary replay over full redo.** When a late boundary appears, the system replays only the boundary triad analysis, not all triads for the affected chapters. This keeps replay cost proportional to the gap size.
