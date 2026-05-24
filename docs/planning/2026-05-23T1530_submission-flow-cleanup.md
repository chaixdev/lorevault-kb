# Submission Flow Code Quality Cleanup

**Date:** May 23, 2026
**Status:** Parked — fix later
**Discovered:** Code walkthrough post durable-ingestion-orchestration implementation
**Walkthrough progress:** Traced from chapter upload → `bootstrapJob` → `SceneDetectionHandler`. Remaining data flow (chunking, embedding, resolution lanes, book reductions, INGESTION_COMPLETE) to be analyzed in a future session.

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

**Fix:** Convert `ChapterValidationResult` to a record. Optionally, collapse both into a single `SubmissionResult` record since they carry overlapping data.

### 3. `createNewChapter` guards mask factory defects

```java
if (chapter.getId() == null) {
    chapter.setId(UUID.randomUUID());
}
if (chapter.getBook() == null && chapter.getBookId() != null) {
    bookRepo.findById(chapter.getBookId()).ifPresent(chapter::setBook);
}
```

If `Chapter.createWithReferences()` doesn't set an ID, that's a bug in the factory — the guard is a workaround. If it does, the guard is dead code. The `bookRepo.findById()` is an eager hydration call that SDN doesn't need (it can persist with `bookId` alone). These should be assertions or removed after verifying `Chapter.createWithReferences()` behavior.

**Fix:** Trace `Chapter.createWithReferences()` to confirm it sets the ID. Remove the null guard. Remove the `bookRepo.findById()` if SDN doesn't need the hydrated `book` reference.

### 4. `prepareChapter` Javadoc references nonexistent "CLI"

> "The caller (CLI) is responsible for invoking individual pipeline steps"

There is no CLI. The caller is `StepExecutionCommandController` — a REST controller for step-by-step execution flow.

**Fix:** Replace "CLI" with "step-by-step execution flow" or "manual step controller."

### 5. `submitChapter` and `prepareChapter` have divergent dedup logic

`submitChapter` checks for existing active jobs and returns the existing job ID rather than creating a duplicate. `prepareChapter` skips this check entirely — calling it twice creates two `ChapterIngestionJob` nodes with two full DAG bootstraps.

After adding dedup to `prepareChapter`, both methods become identical:
```
validateAndProcessChapter → createIngestionJob → return result
```

**Fix:** Extract a single private `doSubmitChapter(bookId, chapterNumber, chapterTitle, chapterText)` that both public methods delegate to. The dedup logic lives in the shared path. The two entry points become thin semantic aliases — or, if the distinction between "submit" and "prepare" is no longer meaningful, collapse into one.

### 6. `bootstrapJob` fetches `stageIds` map but only null-checks, never uses the values

`createAllForJob` returns `Map<StageKey, UUID>` which `bootstrapJob` uses only to verify the root stage ID is non-null before calling `tryTrigger(jobId, root)`. But `tryTrigger` doesn't accept a stage ID — it looks up by `(jobId, step)`. Every key in the map is guaranteed to have a value (all 15 `StageKey.values()` are iterated). The map return serves `rewireEdges` (called internally by `createAllForJob`), not `bootstrapJob`.

**Fix:** Either make `bootstrapJob` simpler:

```java
for (StageKey root : dag.roots()) {
    if (stageRepo.tryTrigger(jobId, root)) {
        eventPublisher.publishEvent(new StageTriggeredEvent(this, jobId, chapterId, root));
    }
}
```

Or, if `createAllForJob` doesn't need to return the map (it's only used internally by `rewireEdges`), change return type to `void`.

### 7. Extract `StageDispatcher` — centralize `onTrigger` across 13 handlers (consolidation point)

**Problem:** 13 handlers × 4 orchestration fields (`StageGraphRepository`, `StageOutputGraphRepository`, `ApplicationEventPublisher`, `PipelineStageSupport`) = 52 injection points. Every `onTrigger` has the identical 4-line guard+idempotency+emit pattern. `@Async` + `@EventListener` duplicated 13 times. 13 different log prefix styles. 13 copies of `System.currentTimeMillis()` manual timing. MDC async propagation already wired (`AsyncConfig.mdcTaskDecorator`) but never populated with stage context.

**Fix:** Introduce a `StageDispatcher` bean that centralizes `onTrigger` once:

```java
@Component
public class StageDispatcher {
    private final Map<StageKey, StageOperation> handlers;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Async
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        MDC.put("stage", event.getStage().name());
        MDC.put("jobId", event.getJobId().toString());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // guard → idempotency → execute → emit
        } finally {
            sample.stop(Timer.builder("ingestion.stage.duration")
                .tag("stage", event.getStage().name()).register(meterRegistry));
            MDC.clear();
        }
    }
}
```

Handlers become pure domain objects — just `execute(jobId, chapterId)`. No `@Async`, no `@EventListener`, no orchestration fields.

**Effect per handler:** removes `onTrigger()`, `@Async`, `@EventListener`, `StageGraphRepository`, `StageOutputGraphRepository`, `ApplicationEventPublisher`, `PipelineStageSupport`, `IngestionJobService` (only used to construct `PipelineStageSupport`), all manual `log.info("[PREFIX]")` (replaced by MDC), all manual `System.currentTimeMillis()` (replaced by `Timer.Sample`). All nested service logs automatically carry `stage` context via MDC (no manual prefixing in `LlmClient`, `SceneDetectionService`, etc.).

**Result:** 52 injection points eliminated, 13 log prefix styles unified to MDC, 13 manual timers replaced by 1 Micrometer timer, `SceneDetectionHandler` drops from 18 fields to 14 (pre-refactor size). Nested service logs gain stage context they never had before.

**Hint:** The 6 persistence services (`Individual/Collective/Object/Location/EventPersistenceService` + `RelationClaimPersistenceService`) are only ever called from `SceneDetectionHandler`, from a single code block with identical signatures. They contribute 6 of the remaining 14 dependencies. Consider extracting an `EntityPersistenceCoordinator` facade that groups these 6 calls into a single `persistAll(scenes, outcome)` method, reducing the handler from 14 to 9 domain dependencies. The 6 services remain as independent, testable classes — the coordinator is a thin delegation facade, same pattern as the dispatcher.

## Files Affected

- `lorevault-core/src/main/java/com/lorevault/api/ingestion/submission/IngestionIsolatedLookupService.java` — delete
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/submission/IngestionService.java` — fold in isolated lookups, clean up `ChapterValidationResult`, fix guards, fix Javadoc, collapse submit/prepare
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/submission/IngestionSubmissionResult.java` — optionally consolidate with `ChapterValidationResult`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/orchestration/StageGraphRepository.java` — `createAllForJob` return type simplification (issue 6)
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/orchestration/IngestionPipelineCoordinator.java` — `bootstrapJob` simplification (issue 6)
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/orchestration/StageDispatcher.java` — new (issue 7)
- 13 handler files — remove orchestration fields, `onTrigger`, `@Async`, `@EventListener` (issue 7)
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/pipeline/PipelineStageSupport.java` — delete (no-op after issue 7)

### 8. Ban `var` — replace with explicit types, codify rule

`SceneDetectionHandler.java:311` and other files use `var` which obscures return types:

```java
var segmentationOutcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
var scenesWithCoords = segmentationOutcome.scenes();
```

The type of `segmentationOutcome` is not obvious from the method name alone. Explicit types make the code self-documenting:

```java
SegmentationOutcome segmentationOutcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
List<SceneWithCoordinates> scenesWithCoords = segmentationOutcome.scenes();
```

**Fix:** Replace all `var` usage with explicit types across the codebase. Add a rule to `docs/rules/coding-standards.md`: "Prefer explicit types over `var`. Only use `var` for obvious right-hand-side constructors where the type is immediately visible on the same line."

### 9. Add container-class grouping guidance to coding standards

Three files use the `final class { private constructor; nested records }` pattern (`TriadAnalysisModels`, `EventCorefModels`, `EventMergeModels`). But the codebase also uses separate top-level records (`content/mention/` has 6, `content/association/` has 10) and private inner records inside services. There's no rule for when to group vs separate.

**Fix:** Add to `docs/rules/code-organization-guidance.md`:

> **Container classes for grouped types**: Use a `public final class` with private constructor as a namespace when:
> - The types form a closed set that always travels together (e.g., LLM response models)
> - Each type is < 20 lines and too thin to justify a separate file
> - The types are only ever referenced through the container (no external direct usage)
>
> Otherwise, use separate top-level records in the same package. Prefer `*Models` suffix for container classes that group LLM deserialization targets.

### 10. Migrate or delete 14 legacy domain events — replace with `StageCompletedEvent`

Handlers now publish `StageCompletedEvent` instead of domain-specific events. But 14 old event classes still exist and two consumers still reference them:

| Event class | Current publisher | Status |
|------------|-------------------|--------|
| `ScenesDetectedEvent` | None (was SceneDetectionHandler) | Dead |
| `ChunksCreatedEvent` | None (was ChunkingHandler) | Dead |
| `EmbeddingsCompletedEvent` | None (was EmbeddingHandler) | Dead |
| 8 resolution/reduction events | None (was resolution/reduction handlers) | Dead |
| `ChapterEventsResolvedEvent` | `ChapterEventAnnRerunService` (bypasses Stage lifecycle) | Active |
| `IngestionFailedEvent` | `PipelineStageSupport` (deleted in issue #7) | Publishes but receiver silent |
| **Consumer:** | | |
| `JobStatusBroadcaster` | Listens to `ScenesDetectedEvent`, `ChunksCreatedEvent`, `IngestionFailedEvent` | **Broken** — never receives them |

**Fix:** Delete 12 dead event classes. Migrate `JobStatusBroadcaster` to listen for `StageCompletedEvent` instead (map `StageKey` → status string). Migrate `ChapterEventAnnRerunService` to publish `StageCompletedEvent` through the coordinator. If `IngestionFailedEvent` has no consumers after `PipelineStageSupport` is deleted, delete it too.

### 11. Simplify LLM call logging — direct call, typed, no event bus

**Current:** `LlmClient` passes string `step` ("chapter-segmentation") through `LlmCallLogger.logCall()` interface → `LlmCallLoggingService` reconstructs `StageKey` via a hand-maintained lookup table (`LLM_STEP_TO_STAGE`) → queries `ChapterIngestionJob` just for `OF_JOB` link → persists `LlmCallRecord`. The type information (`StageKey`) is thrown away at the boundary and reconstructed on the other side.

**Proposed:** `LlmClient` passes `StageKey` directly. `LlmCallLogger.logCall(jobId, StageKey stage, ...)`. `LlmCallLoggingService.persistCall()` finds the Stage via `findByJobIdAndStep` (guaranteed to exist — DAG bootstrapped at job creation), links `LlmCallRecord` via `OF_STAGE`. No event bus, no string mapping, no lookup table, no `jobRepo.findById()`.

Changes:
- `LlmCallLogger` interface: `step: String` → `stage: StageKey`
- `LlmClient` methods: add `StageKey` parameter, propagate to log call
- `LlmCallLoggingService.logCall()`: remove `LLM_STEP_TO_STAGE`, remove `jobRepo.findById()`, use `stageRepo.findByJobIdAndStep()` directly
- Delete `LLM_STEP_TO_STAGE` constant (no longer needed)
- `LlmCallLoggingService` drops from 11 injected dependencies → ~8

### 12. Delete `PipelineStageSupport` — dead weight from pre-coordinator model

Three methods, only one does real work:

| Method | Call sites | Status |
|--------|-----------|--------|
| `updateJobStatus()` | 16 call sites | **No-op** — delegates to `IngestionJobService.updateJobStatus()` which only `log.debug`'s |
| `sanitizeExceptionMessage()` | 14 call sites | **Real behavior** — sanitizes exception messages for logging |
| `runStage()` | 0 call sites | **Dead** — zero callers |

Pre-refactor, `updateJobStatus` wrote `StatusRecord` nodes for pipeline progress tracking and `runStage` wrapped handler execution with `IngestionFailedEvent` emission. Post-refactor, Stage nodes are canonical status and the coordinator handles failure via `StageCompletedEvent`. The class is vestigial.

**Fix:**
1. Extract `sanitizeExceptionMessage(Exception)` to a static utility class (e.g., `ExceptionSanitizer` or add to existing `HashUtils`)
2. Remove all 16 `stageSupport.updateJobStatus(...)` call sites — zero behavioral change, pure dead code
3. Delete `PipelineStageSupport.java` — 12 of 13 handlers lose one injection
4. `IngestionFailedEvent` — covered by issue #10 (zero publishers after this deletion)
5. `IngestionJobService.updateJobStatus()` — also becomes dead (only called via `PipelineStageSupport`), delete it too

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

**Fix:** Replace `buildTriadsForChapter(Chapter)` with `buildTriad(UUID sceneId)` that resolves prev/next in book order:

```
given scene(chapterId, sceneIndex, bookId):
  prev = if sceneIndex > 0: findPrevSceneInChapter(chapterId, sceneIndex)
         elif chapterNumber > 1: findLastSceneOfPreviousChapter(bookId, chapterNumber)
         else: null                     // first scene in book
  next = if not last in chapter: findNextSceneInChapter(chapterId, sceneIndex)
         elif next chapter exists: findFirstSceneOfNextChapter(bookId, chapterNumber)
         else: null                     // last scene in book OR next chapter not yet ingested
```

Symmetric semantics: null means "first scene in book" (no prev), "last scene in book" (no next), or "boundary not yet ingested" (next chapter hasn't arrived — correct, triad analysis will run with null next, edge covered when next chapter's prev resolution kicks in).

**Caller change:** `SceneRelationshipAnalysisService.analyzeChapterTriadsWithIndividuals()` currently iterates `triads = triadBuilder.buildTriadsForChapter(chapter)`. Change to iterate chapter scenes then call `triadBuilder.buildTriad(scene.getId())` for each. Same number of LLM calls, same output shape. Chapter batching happens at the caller level (iteration loop), not the builder level.

**Affected files:**
- `TriadBuilderService.java` — rename/reshape `buildTriadsForChapter` → `buildTriad(UUID sceneId)`, remove `resolveCrossChapterPreviousScene`, add symmetric next resolution, add `Scene findPrev/NextXxx` helpers
- `SceneRelationshipAnalysisService.java` — change caller loop from chapter-batched to scene-batched via `buildTriad`
- `TriadBuilderService` tests (if any) — update signature

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

## Estimated Effort

~225 minutes (excluding issues #10, #11, #15). ~300 minutes total. 4 new files, 40+ files modified, 15+ deleted, 5+ docs updated. Issues 1-6, 8-9, 11 are pure cleanup. Issue 7 is a structural change. Issue 10 deletes 12+ legacy event classes. Issue 13 requires a targeted scan. Issue 14 changes the TriadBuilder API and caller loop. Issue 16 collapses extraction loop boilerplate. Issue 17 renames + deletes dead wrapper (~21 code refs + 5 doc refs).


