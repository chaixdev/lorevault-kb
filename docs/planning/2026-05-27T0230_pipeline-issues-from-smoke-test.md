# Pipeline Issues Discovered During Unified Consolidation Smoke Test

**Date:** May 27, 2026
**Context:** Smoke-testing Deathworlders chapter 001 after the unified entity consolidation refactoring. The test revealed several pre-existing pipeline issues unrelated to the consolidation work itself.

## Issue Inventory

### #1: Triad temporal edge provenance is stubbed (BLOCKER — fixed with placeholder)

**Severity:** Critical (blocks pipeline)
**Root cause:** `GraphTriadAnalysisArtifactLookup.findLatestTriadStageIdByCurrentSceneId()` returns `Optional.empty()` — deliberately stubbed during Stage model migration. The StatusRecord-based correlation (`currentSceneId` composite property) was removed but no Stage-based replacement was implemented.

**Affected code:**
- `TriadAnalysisArtifactLookup.java` / `GraphTriadAnalysisArtifactLookup.java`
- `TriadTemporalEdgeRequestFactory.resolveRequiredProvenance()`

**Current mitigation:** Placeholder returns `null` stageId/llmCallId in `TemporalEdgeProvenance`. Temporal edges are written without proper provenance metadata.

**Proper fix:** Implement Stage-based correlation. Blocked by per-scene `buildTriad` (#14) — need per-scene Stage granularity before a scene→Stage lookup is meaningful.

**Effort:** Medium. Part of #14 buildTriad refactoring. Proper fix ~1-2 days within that context.

**Planning doc reference:** `docs/planning/2026-05-22T2300_durable-ingestion-orchestration.md` deviation #3.

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

### #4: Stage key mislabeling in StageCompletedEvent

**Severity:** Cosmetic (confusing logs, no functional impact)
**Root cause:** `StageCompletedEvent` or `StageOutput` carries a stage key that doesn't match the actual stage. The log shows `BOOK_COLLECTIVE_REDUCTION summary=Detected 6 scenes` and `BOOK_EVENT_CANDIDATE_GENERATION summary=Resolved chapter collectives`.

**Fix approach:** Audit all `StageCompletedEvent` emission points to ensure the correct `StageKey` is set.

**Effort:** Small. ~1-2 hours.

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
| 1 | #1 Proper triad provenance fix | 1-2d | Deferred (blocked by #14) |
| 2 | #2 Orchestrator fires resolution before scene detection | Resolved by #3 fix | ✅ Fixed May 27 |
| 3 | #7 Book-level reductions skip (bookId=null) | ~few hours | ✅ Fixed May 27 |
| 4 | #3 Concurrent scene detection workers | ~few hours | ✅ Fixed May 27 |
| 5 | #5/#6 DATE_TIME coercion | ~2 hours | ✅ Fixed May 27 |
| 6 | #4 Stage key mislabeling | ~2 hours | Open (cosmetic) |

**Fixes applied (May 27):**
- `StageGraphRepository` / `StageOutputGraphRepository`: `safeLocalDateTime()` helper with try-catch fallback for DATE_TIME coercion (#5/#6)
- `IngestionPipelineCoordinator`: `findBookId()` + `resolveBookId()` resolves book ID from chapter→book relationship, propagated through `evaluateDownstream`, `recoverStaleTriggers`, and `recoverStaleRunning` (#7)
- `AsyncConfig`: `sceneDetectionTaskExecutor.maxPoolSize` reduced from 3 to 1 — prevents stale recovery from spawning duplicate workers for the same chapter (#3, which also resolves #2)
