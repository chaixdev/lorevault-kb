# Pipeline Issues Discovered During Unified Consolidation Smoke Test

**Date:** May 27, 2026
**Context:** Smoke-testing Deathworlders chapter 001 after the unified entity consolidation refactoring. The test revealed several pre-existing pipeline issues unrelated to the consolidation work itself.

## Issue Inventory

### #1: Triad temporal edge provenance is stubbed (DEFERRED — partially unblocked by Phase 3c)

**Severity:** Medium (temporal edges written without provenance metadata; no functional block)
**Root cause:** `GraphTriadAnalysisArtifactLookup.findLatestTriadStageIdByCurrentSceneId()` ignores `currentSceneId` and returns the chapter-level `SCENE_SEGMENTATION` stage. `TriadTemporalEdgeRequestFactory.resolveRequiredProvenance()` returns `(jobId, chapterId, null, null)`.

**Affected code:**
- `TriadAnalysisArtifactLookup.java` / `GraphTriadAnalysisArtifactLookup.java`
- `TriadTemporalEdgeRequestFactory.resolveRequiredProvenance()`

**Current mitigation:** Placeholder returns `null` stageId/llmCallId in `TemporalEdgeProvenance`. Temporal edges are written without proper provenance metadata.

**Phase 3c progress:** Per-scene `buildTriad(sceneId)` now uses graph-based prev/next resolution (May 29). The analysis-side blocker is removed.

**Design (May 29):** The fix is `StageExecutionContext` — a first-class execution identity record that flows `stageId` from dispatcher through handlers → services → repositories into every domain node/edge. Provenance lives on the data itself; no per-scene Stage DAG nodes needed. Also eliminates `StageOutput`, implements `deleteDataByStageId`, and aligns with the three-tier observability model.

**Proper fix:** `StageExecutionContext` — a first-class execution identity record (`stageId`, `jobId`, `chapterId`, `bookId`, `stage`) that flows from `StageDispatcher` through handlers → services → repositories into every domain node/edge as a `stageId` property. Provenance lives on the data itself; no lookup needed. Also eliminates `StageOutput` (redundant with `Stage.status`), implements `deleteDataByStageId` for rerun cleanup, and aligns with the project's three-tier observability model.

**Effort:** ~100 files, mostly mechanical (rename `DispatchContext` → `StageExecutionContext`, add `stageId` to domain CREATE queries, delete `StageOutput`).

**Planning doc:** `docs/planning/2026-05-29T0000_stage-execution-context-and-provenance.md`

---

### #2: Orchestrator fires resolution stages before scene detection completes

**Severity:** High (chapter-level resolutions report 0 mentions, book-level report `bookId=null`)
**Root cause:** Pipeline DAG triggers `CHAPTER_*_RESOLUTION` stages as barriers open from earlier completed stages (embedding, chunking, etc.), without waiting for `SCENE_SEGMENTATION` to complete. The scene detection handler runs asynchronously (~45-60s via LLM), but resolution stages fire within milliseconds of submission.

**Evidence (smoke test log):**
```
02:25:56 — CHAPTER_INDIVIDUAL_RESOLUTION completed: 0 chunks from 0 scenes
02:25:56 — CHAPTER_OBJECT_RESOLUTION completed: 0 chunks from 0 scenes
02:25:56 — CHAPTER_LOCATION_RESOLUTION completed: 0 location mentions found
02:25:56 — SceneDetectionService: Chapter segmentation starting...
02:26:53 — SCENE_SEGMENTATION completed: Detected 6 scenes
02:26:57 — CHAPTER_COLLECTIVE_RESOLUTION completed: mentionCount=25, chapterCollectiveCount=13
```

Only `CHAPTER_COLLECTIVE_RESOLUTION` re-ran after scene detection. Other resolution stages had already marked themselves as completed (with 0 counts) and were not re-triggered.

**Affected code:**
- `IngestionPipelineCoordinator.evaluateDownstream()` — barrier evaluation may be missing SCENE_SEGMENTATION as a prerequisite
- `StageDag` — dependency graph may not list SCENE_SEGMENTATION → CHAPTER_*_RESOLUTION edges

**Book-level impact:** `BOOK_*_REDUCTION` handlers receive `bookId=null` because the book ID isn't propagated through the Stage context. Every book-level handler logs `Skipped — Book ID is required`.

**Fix approach:** 
1. Add SCENE_SEGMENTATION as a prerequisite for all CHAPTER_*_RESOLUTION stages in StageDag
2. Propagate `bookId` to book-level stage context in `StageTriggeredEvent`
3. Ensure re-triggering when scene detection completes (stages that ran with 0 counts should be invalidated and re-triggered)

**Effort:** Medium. ~1-2 days. Touches StageDag, IngestionPipelineCoordinator, and possibly handler constructors.

---

### #3: 3 concurrent scene detection workers for same chapter

**Severity:** Low (wastes LLM tokens, doesn't block completion)
**Root cause:** `recoverStaleRunning()` (`@Scheduled`, every 30s) resets RUNNING→TRIGGERED and re-publishes `StageTriggeredEvent` while a handler is mid-execution. The `setRunningConditionally` guard succeeds because the stage is TRIGGERED again. `sceneDetectionTaskExecutor` allows up to 3 threads (maxPoolSize=3).

**Evidence (smoke test log):**
```
02:26:05 — ene-detection-3: Chapter segmentation starting (attempt 2)
02:26:05 — ene-detection-1: Chapter segmentation starting (attempt 3)
02:26:53 — ene-detection-2: Stage completed: Detected 6 scenes
02:26:54 — ene-detection-1: Stage completed: Detected 6 scenes
02:26:56 — ene-detection-3: Stage completed: Detected 6 scenes
```

Each worker independently called the LLM and created scenes (with idempotent persistence).

**Fix approach:** Option A — add in-memory active-stage tracking to prevent stale recovery from re-triggering while a handler is executing. Option B — reduce `sceneDetectionTaskExecutor.maxPoolSize` to 1. Option C — check last-heartbeat rather than just startedAt in stale recovery.

**Effort:** Small. ~few hours.

---

### #4: Stage key mislabeling in StageCompletedEvent — ✅ RESOLVED (May 29 review)

**Severity:** Cosmetic (confusing logs, no functional impact)
**Root cause:** Was a symptom of issue #2 (race condition from concurrent stage completions). The `StageDispatcher.emitComplete()` path is clean — always uses `event.getStage()` from the trigger event. `StepEventMapper` (REST command controller path) has correct `StepKey` → `StageKey` mappings.

**Residual risk:** `StepEventMapper` is an unvalidated parallel emission path. A future refactoring could silently miswire a `StepKey` → `StageKey` mapping. A validation test mapping every `StepKey` to its matching `@ForStage` handler would catch this.

**Effort:** N/A (resolved). Validation test is optional hardening.

---

### #5: DATE_TIME → LocalDateTime coercion in scheduled stale recovery

**Severity:** Low (scheduled task crashes silently, no observability impact on ingestion)
**Root cause:** `StageGraphRepository.mapStageNode()` tries to coerce Neo4j DATE_TIME values to Java `LocalDateTime`, but the driver's `ValueAdapter.asLocalDateTime()` fails for the stored format. The `@Scheduled recoverStaleTriggers()` and `recoverStaleRunning()` methods throw but the exception is swallowed by the scheduler.

**Evidence (smoke test log):**
```
ValueAdapter.asLocalDateTime → StageGraphRepository.mapStageNode:301 → findStaleTriggered:200
Cannot coerce DATE_TIME to LocalDateTime; Error code 'N/A'
```

**Fix approach:** Use `asZonedDateTime().toLocalDateTime()` or `OffsetTime` coercion path. Or store timestamps as strings in Neo4j.

**Effort:** Small. ~1-2 hours.

---

### #6: Job query fails with DATE_TIME coercion

**Severity:** Low (polling returns empty, no functional impact on ingestion)
**Root cause:** Same DATE_TIME coercion issue in `IngestionJobService` when reading `ChapterIngestionJob` nodes.

**Fix approach:** Same as #5 — fix the coercion path.

**Effort:** Same as #5.

---

### #7: Book-level reductions skip due to `bookId=null`

**Severity:** High (book-level entities never created)
**Root cause:** `StageTriggeredEvent` doesn't carry `bookId` for book-level stages. The handler extracts it from `event.getBookId()` which returns `null`.

**Related to:** Issue #2 (orchestrator ordering).

**Fix approach:** Propagate `bookId` from `ChapterIngestionJob` to book-level `StageTriggeredEvent` in the orchestrator. Or extract book ID from the chapter→book relationship in-book handlers.

**Effort:** Part of #2 fix. ~few hours within that context.

---

## Prioritized Action Plan

| Priority | Issue | Effort | Status |
|----------|-------|--------|--------|
| 1 | #1 Proper triad provenance fix | ~100 files (mechanical) | Design ready — see `StageExecutionContext` plan. Pending implementation. |
| 2 | #2 Orchestrator fires resolution before scene detection | Resolved by #3 fix | ✅ Fixed May 27 |
| 3 | #7 Book-level reductions skip (bookId=null) | ~few hours | ✅ Fixed May 27 |
| 4 | #3 Concurrent scene detection workers | ~few hours | ✅ Fixed May 27 |
| 5 | #5/#6 DATE_TIME coercion | ~2 hours | ✅ Fixed May 27 |
| 6 | #4 Stage key mislabeling | N/A | ✅ Resolved (symptom of #2 race condition, confirmed May 29) |

**Fixes applied (May 27):**
- `StageGraphRepository` / `StageOutputGraphRepository`: `safeLocalDateTime()` helper with try-catch fallback for DATE_TIME coercion (#5/#6)
- `IngestionPipelineCoordinator`: `findBookId()` + `resolveBookId()` resolves book ID from chapter→book relationship, propagated through `evaluateDownstream`, `recoverStaleTriggers`, and `recoverStaleRunning` (#7)
- `AsyncConfig`: `sceneDetectionTaskExecutor.maxPoolSize` reduced from 3 to 1 — prevents stale recovery from spawning duplicate workers for the same chapter (#3, which also resolves #2)
