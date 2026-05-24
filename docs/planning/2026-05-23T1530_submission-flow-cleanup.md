# Submission Flow Code Quality Cleanup

**Date:** May 23, 2026
**Status:** Active — planning complete, ready for execution
**Discovered:** Code walkthrough post durable-ingestion-orchestration implementation
**Walkthrough progress:** Traced from chapter upload → `bootstrapJob` → `SceneDetectionHandler`. Remaining data flow (chunking, embedding, resolution lanes, book reductions, INGESTION_COMPLETE) to be analyzed in a future session (#18).
**Design note:** Walkthrough surfaced a paradigm tension between service-oriented (LLM default) and richer domain model design. Captured in [Service-Oriented vs Rich Domain Model](../concepts/service-oriented-vs-domain-model.md).

## Extracted Documents

Three high-impact focuses have been extracted to their own design docs for readability and independent execution:

| Document | Covers | Why extracted |
|----------|--------|---------------|
| [Quick Wins](2026-05-24T0000_submission-cleanup-quick-wins.md) | Issues #4, #6, #8, #11, #12(sanitize+updateJobStatus), #17, #22 | Low risk, can execute immediately |
| [StageDispatcher Extraction](2026-05-24T0000_stagedispatcher-extraction.md) | Issues #7 + #20 | Highest-value structural change, needs standalone PR |
| [SSE Event Migration](2026-05-24T0000_sse-event-migration.md) | Issue #10a | Bug fix (broken SSE), not structural cleanup |

## Oracle Review (May 24, 2026) — Second Pass

**Verdict:** 16 of 20 active issues directionally correct. Specific adjustments:

| Issue | Finding |
|-------|---------|
| **#3** | Both guards are dead — `Chapter.createWithReferences()` sets ID unconditionally, and `bookRepo.findById()` hydration is unnecessary (SDN 7.x uses `bookId` alone for `IN_BOOK`). Remove both. |
| **#5** | Do NOT collapse `submitChapter`/`prepareChapter` — they serve different callers. Extract shared `doSubmitChapter()`, keep both public methods. |
| **#6** | Do NOT change `createAllForJob` return type to void — needed by `rewireEdges` in `rerunStage`. Just remove the dead null guard in `bootstrapJob`. |
| **#7/#20** | StageDispatcher needs explicit error boundary (catch unchecked exceptions from `handler.execute()`, emit `StageCompletedEvent` with failure). Executor routing question: single vs per-stage executors. Extracted to [dedicated doc](2026-05-24T0000_stagedispatcher-extraction.md). |
| **#10a** | `IngestionFailedEvent` is already dead (not "after #12" — `runStage()` has zero callers). SSE is already broken. Promoted to bug fix, moved to Phase 2. Extracted to [dedicated doc](2026-05-24T0000_sse-event-migration.md). |
| **#11** | `LlmCallLoggingService` drops from 4→3 injections (not 11→8). Plan overstated. |
| **#12** | `IngestionJobService.updateJobStatus()` has `@Transactional(REQUIRES_NEW)` for `log.debug()` only — active transaction pollution bug, not just dead code. Remove call sites in Phase 1. |
| **#14** | LLM call count is NOT the same — symmetric next resolution adds one extra LLM call per chapter boundary. Plan claim is incorrect; verify intent before executing. |

**Critical omissions identified:** `safeMessage()` duplication across 3 classes (G1), `SceneDetectionHandler` does 2 chapter lookups when 1 would suffice (G5), `IngestionStatus` enum may have orphaned values after #12 (G6), test file cleanup scope incomplete (G7).

**Incorporated into this doc and extracted docs below.**

## Issues

### 1. `IngestionIsolatedLookupService` — hyper-defensive, pays no weight

Three methods, each wrapping a trivial Neo4j read in a try-catch that converts any exception to `ChapterSubmissionLookupException` with detailed `IngestionFailure` metadata. The `REQUIRES_NEW` transaction isolation doesn't protect the caller — exceptions propagate regardless. If the lookup fails, the submission fails. The separation adds complexity with zero benefit.

**Fix:** Delete `IngestionIsolatedLookupService.java`. Fold into `IngestionService` as a private helper:

```java
private <T> T isolatedLookup(Supplier<T> query, String code, String message) {
    try { return query.get(); }
    catch (Exception e) { throw new ChapterSubmissionLookupException(...); }
}
```

### 2. `ChapterValidationResult` — inconsistent with `IngestionSubmissionResult`

`IngestionSubmissionResult` is a clean 2-field Java record. `ChapterValidationResult` is a static inner class with manual constructor, manual getters, factory methods, and `@Getter` on some fields but not others. Both serve the same purpose (thin value objects). They should be consistent.

**Fix:** Convert `ChapterValidationResult` to a record. **Do not** collapse with `IngestionSubmissionResult` — they carry different data for different callers.

### 3. `createNewChapter` guards mask factory defects

```java
if (chapter.getId() == null) {
    chapter.setId(UUID.randomUUID());
}
if (chapter.getBook() == null && chapter.getBookId() != null) {
    bookRepo.findById(chapter.getBookId()).ifPresent(chapter::setBook);
}
```

**Oracle verified:** `Chapter.createWithReferences()` sets `chapter.id = UUID.randomUUID()` unconditionally (line 310). The ID null-guard is dead code — remove it. The `bookRepo.findById()` hydration is also unnecessary: SDN 7.x creates `IN_BOOK` relationships from `bookId` alone. Nobody reads `chapter.getBook()` in this code path before `chapterRepo.save()` — the hydrated entity is discarded.

**Fix:** Remove both guards. No verification needed — the oracle traced both code paths.

### 4. `prepareChapter` Javadoc references nonexistent "CLI"

> "The caller (CLI) is responsible for invoking individual pipeline steps"

There is no CLI. The caller is `StepExecutionCommandController` — a REST controller for step-by-step execution flow.

**Fix:** Replace "CLI" with "step-by-step execution flow" or "manual step controller."

### 5. `submitChapter` and `prepareChapter` have divergent dedup logic

`submitChapter` checks for existing active jobs and returns the existing job ID rather than creating a duplicate. `prepareChapter` skips this check entirely — calling it twice creates two `ChapterIngestionJob` nodes with two full DAG bootstraps.

**Oracle adjustment:** Do NOT collapse the two methods. They serve different callers:
- `submitChapter` — async pipeline entry point (API-driven ingestion)
- `prepareChapter` — step-by-step CLI/controller entry point

`prepareChapter`'s Javadoc says "does not publish ChapterIngestionEvent" but `createIngestionJob` internally calls `bootstrapJob()` which emits `StageTriggeredEvent`. The distinction may be moot in the current durable model, but the entry points remain semantically different. Collapsing them creates a single method that can't distinguish between "auto-trigger" and "manual step control" callers.

**Fix:** Extract a single private `doSubmitChapter(bookId, chapterNumber, chapterTitle, chapterText)` that both public methods delegate to. The dedup logic lives in the shared path. Keep both public methods as distinct semantic entry points.

### 6. `bootstrapJob` fetches `stageIds` map but only null-checks, never uses the values

`createAllForJob` returns `Map<StageKey, UUID>` which `bootstrapJob` uses only to verify the root stage ID is non-null before calling `tryTrigger(jobId, root)`. But `tryTrigger` doesn't accept a stage ID — it looks up by `(jobId, step)`. Every key in the map is guaranteed to have a value (all 15 `StageKey.values()` are iterated). The map return serves `rewireEdges` (called internally by `createAllForJob`), not `bootstrapJob`.

**Fix:** Simplify `bootstrapJob` — remove the dead null guard on `rootId`:

```java
for (StageKey root : dag.roots()) {
    if (stageRepo.tryTrigger(jobId, root)) {
        eventPublisher.publishEvent(new StageTriggeredEvent(this, jobId, chapterId, root));
    }
}
```

**Oracle adjustment:** Do NOT change `createAllForJob` return type to `void`. The returned map is needed by `rewireEdges` (called internally) and `rerunStage` (line 234). Keep the map return — just simplify `bootstrapJob`.

### 7. Extract `StageDispatcher` — centralize `onTrigger` across 13 handlers (consolidation point)

**Problem:** 13 handlers × 4 orchestration fields (`StageGraphRepository`, `StageOutputGraphRepository`, `ApplicationEventPublisher`, `PipelineStageSupport`) = 52 injection points. Every `onTrigger` has the identical 4-line guard+idempotency+emit pattern. `@Async` + `@EventListener` duplicated 13 times. 13 different log prefix styles. 13 copies of `System.currentTimeMillis()` manual timing. MDC async propagation already wired (`AsyncConfig.mdcTaskDecorator`) but never populated with stage context.

**Fix:** Introduce a `StageDispatcher` bean that centralizes `onTrigger` once. **Extracted to [StageDispatcher Extraction](2026-05-24T0000_stagedispatcher-extraction.md).** Key design points:

1. `StageOperation` interface — single `execute(DispatchContext ctx)` method
2. `DispatchContext` record — carries `(jobId, chapterId, bookId, stage)`
3. Handlers self-register via `@ForStage(StageKey)` annotation
4. Dispatcher sets MDC context (stage + jobId) for unified logging
5. Single Micrometer `Timer.Sample` replaces 13 manual `System.currentTimeMillis()`
6. **CRITICAL — error boundary:** Dispatcher must catch unchecked exceptions from `handler.execute()` and emit `StageCompletedEvent` with failure — otherwise stages hang RUNNING for 300s
7. **CRITICAL — no @Transactional on onTrigger:** LLM calls take 30-120s, each handler manages its own transactions
8. Open question: single executor vs per-stage executor routing (currently 2 executors)

### 8. Replace unclear `var` usage with explicit types

33 `var` usages across 8 files. Most are "obvious RHS constructor" cases (`var cfg = models.nlpBig()`). The real issue is the ~3-4 cases where the return type isn't obvious from the method name:

```java
var segmentationOutcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
var scenesWithCoords = segmentationOutcome.scenes();
```

**Fix:** Replace unclear `var` usages with explicit types. Don't codify a blanket ban — Java's `var` is stable since Java 10. Add guidance to coding standards: "Prefer explicit types when the RHS doesn't make the type immediately obvious."

### 9. ~~Add container-class grouping guidance to coding standards~~ ✅ **RESOLVED — added to `docs/rules/code-organization-guidance.md`**

~~Three files use the `final class { private constructor; nested records }` pattern (`TriadAnalysisModels`, `EventCorefModels`, `EventMergeModels`). But the codebase also uses separate top-level records (`content/mention/` has 6, `content/association/` has 10) and private inner records inside services. There's no rule for when to group vs separate.~~

~~**Fix:** Add to `docs/rules/code-organization-guidance.md`:~~

> **Container classes for grouped types**: Use a `public final class` with private constructor as a namespace when:
> - The types form a closed set that always travels together (e.g., LLM response models)
> - Each type is < 20 lines and too thin to justify a separate file
> - The types are only ever referenced through the container (no external direct usage)
>
> Otherwise, use separate top-level records in the same package. Prefer `*Models` suffix for container classes that group LLM deserialization targets.

### 10a. Delete 12 dead legacy event classes, fix `JobStatusBroadcaster` SSE ⚠️ LIVE BUG

**Oracle finding:** SSE is already broken — `IngestionFailedEvent` is already dead (not "after #12"), `runStage()` has zero callers. This is a bug fix, not cleanup. **Extracted to [SSE Event Migration](2026-05-24T0000_sse-event-migration.md).**

Handlers now publish `StageCompletedEvent` instead of domain-specific events. But `JobStatusBroadcaster` listens for `IngestionEvent` and only handles the 4 old event types — none of which are published anymore.

**Fix:** Migrate `JobStatusBroadcaster` to listen for `StageCompletedEvent`. Map `StageKey` → human-readable status. Delete 12 dead event classes. Update ~15 test files.

**Files affected:** 1 broadcaster, 12 event classes deleted, ~15 test files updated.

### 10b. Migrate `ChapterEventAnnRerunService` through coordinator (separate task)

`ChapterEventAnnRerunService` publishes `ChapterEventsResolvedEvent` directly, bypassing the coordinator's `StageTriggeredEvent` → handler → `StageCompletedEvent` lifecycle. This means reruns don't get durable stage tracking, recovery, or SSE broadcasting.

**Not a simple fix** because:
- `ChapterEventAnnRerunService` generates a synthetic `jobId` and `correlationId` — it's not a real `ChapterIngestionJob`
- To go through the coordinator, it would need a real job with stages bootstrapped
- The rerun path is critical for the event coref/merge pipeline

**Fix (separate design discussion):** Evaluate whether reruns should create real `ChapterIngestionJob` nodes with stage DAGs, or continue as a lightweight path with a dedicated SSE feed.

### 11. Simplify LLM call logging — direct call, typed, no event bus

**Oracle correction:** `LlmCallLoggingService` drops from ~4→3 injections (removes `jobRepo`), not 11→8. The plan overstated.

**Fix:** `LlmCallLogger.logCall(jobId, StageKey stage, ...)` instead of `(jobId, String step, ...)`. `LlmCallLoggingService.persistCall()` uses `stageRepo.findByJobIdAndStep()` directly — no string mapping, no lookup table, no `jobRepo.findById()`.

Files: `LlmCallLogger`, `LlmClient`, `LlmCallLoggingService`. Delete `LLM_STEP_TO_STAGE`.

### 12. Delete `PipelineStageSupport` — dead weight from pre-coordinator model

**Oracle finding:** `IngestionJobService.updateJobStatus()` has `@Transactional(propagation = REQUIRES_NEW)` for `log.debug()` only — this is an active transaction pollution bug, not just dead code. Remove call sites in Phase 1 (part of [Quick Wins](2026-05-24T0000_submission-cleanup-quick-wins.md)), delete class in Phase 2.

Three methods, only one does real work:

| Method | Call sites | Status |
|--------|-----------|--------|
| `updateJobStatus()` | 16 call sites | **No-op** — delegates to `IngestionJobService.updateJobStatus()` which only `log.debug`'s, with unnecessary `REQUIRES_NEW` |
| `sanitizeExceptionMessage()` | 14 call sites | **Real behavior** — sanitizes exception messages for logging |
| `runStage()` | 0 call sites | **Dead** — zero callers |

**Fix:**
1. Extract `sanitizeExceptionMessage(Exception)` to `ExceptionSanitizer` utility class (Phase 1 — [Quick Wins](2026-05-24T0000_submission-cleanup-quick-wins.md))
2. Remove all 16 `stageSupport.updateJobStatus(...)` call sites — zero behavioral change, pure dead code (Phase 1)
3. Delete `PipelineStageSupport.java` (Phase 2)
4. Delete `IngestionJobService.updateJobStatus()` — dead after #2 (Phase 2)
5. Audit `IngestionStatus` enum for orphaned values after #2 (Phase 2)
6. Delete `PipelineStageSupportTest.java`, update all handler test mocks (Phase 2)

**Gap identified (G1):** `safeMessage()` is duplicated in `IngestionService`, `IngestionIsolatedLookupService`, and `PipelineStageSupport`. Consolidate into `ExceptionSanitizer` alongside `sanitizeExceptionMessage()`.

### 13. Find and eliminate handler→service guard duplication

**Problem:** Handlers duplicate preconditions their delegates already check. Example: `SceneDetectionHandler.detectAndPersistScenes()` guards against `chapter.getRawText() == null || isEmpty()` before calling `sceneDetectionService.detectScenesInChapter()`, which has the identical guard (line 70-73). The handler returns `List.of()`, the service returns `SceneSegmentationOutcome(emptyList())` — same behavior, doubled maintenance.

This is a broader pattern risk: hand-rolled handler preambles that re-implement domain constraints owned by the service. As services evolve their guards, handlers drift.

**Fix:**
1. **Scan:** Audit all handler `execute()` methods (13 handlers) and their immediate delegate calls. For each, check whether the handler checks a precondition (null, empty, etc.) that the delegate also checks. Identify all violations — not just `SceneDetectionHandler`.
2. **Remove:** Delete the handler-side guard. Let the service handle the constraint. No behavior change — the service already returns correct empty/early-exit results.
3. **Verify:** For any handler where removing the guard would change behavior (e.g., service throws instead of returning empty), log a note in the walkthrough findings and skip — those are legitimate handler-level decisions.

**Affected files (known):**
- `SceneDetectionHandler.java:284-288` — remove empty-text guard (duplicated in `SceneDetectionService.java:70-73`)
- Other handlers TBD by scan

### 14. Replace `buildTriadsForChapter` with `buildTriad(Scene)` — remove chapter-aware abstraction

**Problem:** `TriadBuilderService.buildTriadsForChapter(Chapter)` is a chapter-scoped API for a concept that isn't chapter-scoped. Triads are sliding windows over scenes in book reading order — scene adjacency doesn't care about chapter boundaries. The chapter parameter is an input convenience (chapters are the unit of ingestion) dressed as a domain abstraction.

Evidence the abstraction leaks:
- `resolveCrossChapterPreviousScene` reaches across chapter boundaries for proper `prev` resolution
- Last scene of every chapter has `next = null` — no symmetric `resolveCrossChapterNextScene`
- The asymmetry means chapter-last scenes lack "next" context during entity extraction, and the N[l]→N+1[0] temporal edge is only analyzed from the N+1 side (redundant per-scene coverage, but asymmetric)

By the time triad analysis runs, all scenes are persisted. The correct unit of triad construction is a scene, not a chapter.

**⚠️ Oracle adjustment — LLM call count is NOT the same:** The plan claims "Same number of LLM calls, same output shape." This is incorrect for the symmetric next-resolution case. When chapter N+1 has already been ingested, the last-scene triad of chapter N will now include the first scene of chapter N+1 as `next` (currently `null`). This adds one extra LLM call per chapter boundary. Verify this is intentional before executing.

**Fix:** Replace `buildTriadsForChapter(Chapter)` with `buildTriad(UUID sceneId)` that resolves prev/next in book order. Caller change: iterate chapter scenes then call `buildTriad(scene.getId())` for each.

**Affected files:**
- `TriadBuilderService.java` — rename/reshape, add symmetric next resolution, add `Scene findPrev/NextXxx` helpers
- `SceneRelationshipAnalysisService.java` — change caller loop from chapter-batched to scene-batched
- `TriadBuilderService` tests — update signature

### 15. Scene index: chapter-scoped vs. book-scoped (follow-up decision)

Scenes currently have `sceneIndex` that is chapter-scoped (index within chapter, resetting per chapter). No book-level index exists. `buildTriad(Scene)` derives book order by composing `chapterNumber` + `sceneIndex`, which works but is fragile:

- Two scenes across different chapters can only be compared via `(chapterNumber, sceneIndex)` → requires chapter ordering data
- If chapters are reordered or sceneIndex semantics change, triad ordering breaks silently
- Queries for "previous N scenes" across the book require chapter-aware traversal

**Decision point (not action):** Whether to add a `bookSequenceIndex` field on Scene for book-level absolute ordering. This would make `buildTriad(Scene)` trivial (prev/next = index ± 1) and simplify cross-chapter queries. Cost: duplicate index maintenance, migration complexity.

**Not part of this cleanup task** — added as a parked decision for discussion.

### 16. Collapse repeated extraction loop patterns in `SceneRelationshipAnalysisService`

**Problem:** `analyzeChapterTriadsWithIndividuals()` has two 6× repeated blocks (one per entity type: individuals, locations, objects, collectives, events, relationClaims):

**Block 1 — collect (lines 216-257):** normalize → guard-empty → merge into `Map<Integer, List<T>>`:
```java
List<TriadAnalysisModels.IndividualExtraction> triadIndividuals = normalizeIndividuals(normalized);
if (!triadIndividuals.isEmpty()) {
    extractedIndividualsBySceneIndex
        .computeIfAbsent(sceneIndex, key -> new ArrayList<>())
        .addAll(triadIndividuals);
}
// ... same 5 more times
```

**Block 2 — coalesce (lines 260-288):** `Map<Integer, List<T>>` → stream → wrap → sort → toList:
```java
List<TriadAnalysisModels.SceneIndividualExtraction> sceneIndividualExtractions =
    extractedIndividualsBySceneIndex.entrySet().stream()
        .map(e -> new TriadAnalysisModels.SceneIndividualExtraction(e.getKey(), List.copyOf(e.getValue())))
        .sorted(Comparator.comparingInt(TriadAnalysisModels.SceneIndividualExtraction::sceneIndex))
        .toList();
// ... same 5 more times
```

**Fix:**
1. **Merge helper** — trivial generics, no model changes:
   ```java
   private static <T> void mergeIfNotEmpty(Map<Integer, List<T>> bucket, int sceneIndex, List<T> items) {
       if (!items.isEmpty()) {
           bucket.computeIfAbsent(sceneIndex, k -> new ArrayList<>()).addAll(items);
       }
   }
   ```
   Six 6-line blocks → six 1-liners. Eliminates the `isEmpty()` guard at each call site.

2. **Coalesce helper** — requires a common `sceneIndex()` accessor. Two options:
   - **A — sealed interface** on the records: `sealed interface SceneExtraction { int sceneIndex(); }` implemented by all six `Scene*Extraction` records. Generic coalesce:
     ```java
     private static <T extends SceneExtraction> List<T> coalesce(
         Map<Integer, List<?>> bucket, BiFunction<Integer, List<?>, T> constructor) {
         return bucket.entrySet().stream()
             .map(e -> constructor.apply(e.getKey(), List.copyOf(e.getValue())))
             .sorted(Comparator.comparingInt(SceneExtraction::sceneIndex))
             .toList();
     }
     ```
   - **B — collector record** bundling normalize + wrap + bucket. A `record ExtractionPipe<T, S>(Function<TriadStructuredResult, List<T>> normalize, BiFunction<Integer, List<T>, S> wrap, Map<Integer, List<T>> bucket)` array of 6. Loop over the array — 1 inner loop for both collect and coalesce phases. More abstract but eliminates all type-level repetition.

   Option A is simpler and makes `TriadAnalysisModels` self-documenting.

**Affected files:**
- `SceneRelationshipAnalysisService.java` — add `mergeIfNotEmpty` helper, add `coalesce` helper, collapse 12 repeated blocks
- `TriadAnalysisModels.java` — add `sealed interface SceneExtraction` and implement on 6 records (if option A)

### 17. Rename `analyzeChapterTriadsWithIndividuals` → `analyzeChapterTriads`, delete dead wrapper

**Problem:** Two naming issues in `SceneRelationshipAnalysisService`:

1. `analyzeChapterTriadsWithIndividuals` handles all six entity types (individuals, locations, objects, collectives, events, relationClaims) but name implies individuals-only — historical artifact from when the method only handled individuals + locations. The 2-arg overload (line 152) delegates to the 3-arg overload (line 157) with a no-op callback. Both are misleadingly named.

2. `analyzeChapterTriads(UUID, Chapter)` at line 301 is a thin wrapper that calls `analyzeChapterTriadsWithIndividuals(jobId, chapter).triadAnalyses()` and discards all entity extraction results. It has **zero production callers** — only 10 test call sites in `SceneRelationshipAnalysisServiceTest`. Dead production code.

**Fix:**
1. Rename `analyzeChapterTriadsWithIndividuals` → `analyzeChapterTriads` (both overloads) — it's the canonical triad analysis method, it handles everything
2. Delete the current `analyzeChapterTriads` wrapper — callers use `analyzeChapterTriads(...).triadAnalyses()` instead
3. Update 2 production call sites (`SceneDetectionHandler.java:226, 335`) + 11 test call sites (8 in `SceneDetectionHandlerTest`, 2 in `IndividualResolutionIT`, 1 in `SceneRelationshipAnalysisServiceTest`) + 8 doc references

**Blast radius:** 21 code references + 8 doc references. No API behavior change — pure rename.

**Affected files:**
- `SceneRelationshipAnalysisService.java` — rename 2 overloads, delete dead wrapper
- `SceneDetectionHandler.java` — update 2 call sites
- `SceneDetectionHandlerTest.java` — update 8 mock/verify calls
- `IndividualResolutionIT.java` — update 2 mock calls
- `SceneRelationshipAnalysisServiceTest.java` — update 1 direct call + 10 `analyzeChapterTriads` calls (chain `.triadAnalyses()`)
- 5 doc files (planning, patterns, archive)

### 18. Complete walkthrough — remaining 12 handlers

The walkthrough paused at `SceneDetectionHandler` persistence block. Issues #12 (delete `PipelineStageSupport`), #13 (guard duplication scan), and the `StageDispatcher` hint (#7) require a complete scan of the remaining pipeline stages: chunking, embedding, 6 resolution lanes, 3 book-reduction, 2 event lanes. The walkthrough may reveal additional `PipelineStageSupport` call sites, additional guard duplications, and additional handler patterns not yet captured.

**Action (prerequisite):** Complete the walkthrough before executing issues #7, #12, #13, or #14.

### 19. Add test impact analysis

Issues #7 (StageDispatcher), #10a (delete events), #12 (delete PipelineStageSupport), #14 (buildTriad API change), and #17 (rename method) will break tests. Before execution, grep for all references to deleted/moved types and include test updates in scope.

**Affected test files (known):**
- `SceneDetectionHandlerTest.java` — references `PipelineStageSupport`, `analyzeChapterTriadsWithIndividuals`
- `SceneRelationshipAnalysisServiceTest.java` — references `analyzeChapterTriads`, `analyzeChapterTriadsWithIndividuals`
- `IndividualResolutionIT.java` — references `analyzeChapterTriadsWithIndividuals`
- Other handler tests TBD by walkthrough + grep

### 20. Document StageDispatcher transaction boundary

The dispatcher's `onTrigger` runs `@Async` + `@EventListener`. The coordinator publishes `StageTriggeredEvent` after its own transaction commits. The handler's `execute()` calls `@Transactional` services — each handler manages its own transactions. The dispatcher's `onTrigger` must **NOT** be `@Transactional` — wrapping handler execution in a dispatcher-level transaction would cause issues with long-running LLM calls, nested transaction semantics, and error handling.

**Action:** State this requirement in the dispatcher design (issue #7) and add a comment in the code: "This method must NOT be @Transactional — each handler manages its own transactions."

## Sequencing

### Phase 1 — Quick Wins (extracted to [dedicated doc](2026-05-24T0000_submission-cleanup-quick-wins.md))

Low-risk, high-certainty items. Execute immediately — no walkthrough prerequisite.

| Issue | Item | Estimate |
|-------|------|----------|
| #4 | Javadoc "CLI" fix | 1 line |
| #6 | bootstrapJob map simplification | ~5 lines |
| #8 | Unclear var → explicit types (3 cases) | 3 lines |
| #11 | LLM call logging — type-safe StageKey | ~10 lines |
| #12a | Extract `sanitizeExceptionMessage` to `ExceptionSanitizer` | New utility class |
| #12b | Remove 16 `stageSupport.updateJobStatus(...)` call sites | 16 deletions |
| #17 | Rename `analyzeChapterTriadsWithIndividuals` → `analyzeChapterTriads` | 22 files |
| #22 | Rename `replayBoundaryTemporalProjection` → `enrichCrossChapterTemporalEdges` | 1 file |

**Combined:** ~30 min, ~13 files modified, 1 new utility class.

### Phase 2 — Post-Walkthrough Cleanup (#18 prerequisite)

Walkthrough must be completed first to reveal additional `PipelineStageSupport` call sites, guard duplications, and handler patterns.

| Issue | Item | Notes |
|-------|------|-------|
| **#18** | Complete walkthrough | **BLOCKING** — run first |
| **#19** | Test impact analysis | **BLOCKING** — run after walkthrough |
| #1 | Delete `IngestionIsolatedLookupService`, fold into `IngestionService` | |
| #2 | Record-ify `ChapterValidationResult` | |
| #3 | Remove both dead guards (ID + book hydration) | Oracle confirmed both dead |
| #5 | Extract shared `doSubmitChapter()`, keep both public methods | Oracle: don't collapse |
| #10a | Fix broken SSE + delete 12 dead event classes | Extracted to [SSE doc](2026-05-24T0000_sse-event-migration.md). Promoted from Phase 3 — bug fix. |
| #12c | Delete `PipelineStageSupport.java`, `IngestionJobService.updateJobStatus()`, `PipelineStageSupportTest.java` | Phase 1 removed call sites + extracted sanitize |
| #13 | Scan + eliminate handler→service guard duplication | Walkthrough reveals all handlers |
| #16 | Collapse extraction loop patterns | Option A (sealed interface) |
| #21 | Scan + eliminate unnecessary intermediate result records | Sequence AFTER #16 |

### Phase 3 — Structural Changes (separate PRs)

| Issue | Item | Notes |
|-------|------|-------|
| **#7/#20** | StageDispatcher extraction | Extracted to [StageDispatcher doc](2026-05-24T0000_stagedispatcher-extraction.md). Highest value, highest risk. Standalone PR. |
| **#14** | `buildTriadsForChapter` → per-scene `buildTriad` | Changes core triad logic. **Verify LLM call count impact before executing.** |
| #10b | `ChapterEventAnnRerunService` migration through coordinator | Design discussion needed. Documented known-gap. |

### Parked / Skipped

| Issue | Status |
|-------|--------|
| #9 | Resolved — container-class guidance in `code-organization-guidance.md` |
| #15 | Deferred — book-scoped scene index is a decision point, not a task |
| #8 (blanket ban) | Scaled back — fix ~3 unclear cases only, add coding standards guidance |

## Estimated Effort

| Phase | Time | Files |
|-------|------|-------|
| Phase 1 — Quick wins | ~30 min | ~13 modified, 1 new |
| Phase 2 — Post-walkthrough | ~150 min | 25+ modified, 15+ deleted |
| Phase 3 — Structural | ~120 min | 20+ modified, 4 new, 1 deleted |
| **Total** | **~300 min** | **50+ modified, 16+ deleted, 5 new** |


