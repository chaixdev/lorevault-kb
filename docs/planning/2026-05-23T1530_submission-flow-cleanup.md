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

### 7. 13 handlers × 4 orchestration fields = 52 duplicated injection points

`SceneDetectionHandler` has 18 fields. 14 are domain logic inherited from before the refactor. 4 (`StageGraphRepository`, `StageOutputGraphRepository`, `ApplicationEventPublisher`, `PipelineStageSupport`) were added by the durable orchestration refactor — and every other handler received the same 4. Every `onTrigger` method has the identical 4-line guard+idempotency+emit pattern.

**Fix:** Introduce a `StageDispatcher` bean that centralizes `onTrigger` once:

```java
@Component
public class StageDispatcher {
    private final Map<StageKey, StageOperation> handlers;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        // guard → idempotency → execute → emit
        // ... one copy of the pattern ...
    }
}
```

Handlers become pure domain objects — just `execute(jobId, chapterId)`. No `@Async`, no `@EventListener`, no orchestration fields. `PipelineStageSupport` can be deleted (already a no-op). Removes 52 injection points, 13 copies of the template, and 13 annotations.

Result: `SceneDetectionHandler` drops from 18 fields back to 14 (pre-refactor size).

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

## Estimated Effort

~135 minutes (excluding issues #10, #11). ~210 minutes total. 3 new files, 30+ files modified, 15+ deleted, 1 doc updated. Issues 1-6, 8-9, 11 are pure cleanup. Issue 7 is a structural change. Issue 10 deletes 12+ legacy event classes.
