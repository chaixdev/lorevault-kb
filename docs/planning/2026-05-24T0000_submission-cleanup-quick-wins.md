# Submission Cleanup — Quick Wins (Phase 1)

**Date:** May 24, 2026
**Status:** Ready to execute
**Parent:** [Submission Flow Code Quality Cleanup](2026-05-23T1530_submission-flow-cleanup.md)
**Oracle reviewed:** May 24, 2026 — all items confirmed low risk, correct direction.

These are the lowest-risk, highest-certainty items from the full cleanup plan. Each can be executed independently with minimal blast radius. Combined: ~30 minutes, ~8 files modified, 1 new utility class.

---

## QW1: Fix Javadoc — "CLI" → "step-by-step execution flow"

**Source issue:** #4

**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/submission/IngestionService.java:123`

**Current:**
```java
The caller (CLI) is responsible for invoking individual pipeline steps via their Operation interfaces.
```

**Fix:**
```java
The caller (step-by-step execution controller) is responsible for invoking individual pipeline steps via their Operation interfaces.
```

---

## QW2: Simplify `bootstrapJob` map usage

**Source issue:** #6

**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/orchestration/IngestionPipelineCoordinator.java:174-196`

**Current:**
```java
Map<StageKey, UUID> stageIds = stageRepo.createAllForJob(jobId, dag);
// ...
for (StageKey root : dag.roots()) {
    UUID rootId = stageIds.get(root);
    if (rootId != null) {  // dead — every key guaranteed present
        boolean triggered = stageRepo.tryTrigger(jobId, root);
        // ...
    }
}
```

**Fix:** Remove the null guard on `rootId`. Every `StageKey.values()` is iterated by `createAllForJob`, so every key in the map is guaranteed present.

```java
Map<StageKey, UUID> stageIds = stageRepo.createAllForJob(jobId, dag);
// emit triggers for root stages
for (StageKey root : dag.roots()) {
    boolean triggered = stageRepo.tryTrigger(jobId, root);
    if (triggered) { /* ... */ }
}
```

**Note:** Do NOT change `createAllForJob` return type to `void` — the returned map is used by `rewireEdges` (called internally) and `rerunStage` (line 234).

---

## QW3: Replace unclear `var` usages with explicit types

**Source issue:** #8 (scaled back)

**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/scene/SceneDetectionHandler.java`

Three `var` usages where the type isn't obvious from the RHS:

| Line | Current | Type |
|------|---------|------|
| 292 | `var segmentationOutcome = sceneDetectionService.detectScenesInChapter(...)` | `SceneDetectionService.SceneSegmentationOutcome` |
| 293 | `var scenesWithCoords = segmentationOutcome.scenes()` | `List<SceneDetectionService.SceneWithCoordinates>` |
| 336 | `var replayOutcome = sceneRelationshipAnalysisService.analyzeChapterTriadsWithIndividuals(...)` | `TriadAnalysisModels.SceneRelationshipOutcome` |

**Fix:** Replace each with the explicit type. Add guidance to coding standards: "Prefer explicit types when the RHS doesn't make the type immediately obvious."

---

## QW4: Simplify LLM call logging — pass `StageKey` directly

**Source issue:** #11

**Problem:** `LlmClient` passes `String step` ("chapter-segmentation") through `LlmCallLogger.logCall()` → `LlmCallLoggingService` reconstructs `StageKey` via hand-maintained `LLM_STEP_TO_STAGE` map → queries `ChapterIngestionJob` just for `OF_JOB` link → persists `LlmCallRecord`. The type information (`StageKey`) is thrown away and reconstructed.

**Fix:**

1. **`LlmCallLogger` interface** — change `logCall(jobId, String step, ...)` → `logCall(jobId, StageKey stage, ...)`
2. **`LlmClient`** — pass `StageKey` instead of string
3. **`LlmCallLoggingService.logCall()`** — remove `LLM_STEP_TO_STAGE` map, remove `jobRepo.findById()`, use `stageRepo.findByJobIdAndStep(jobId, stage)` directly
4. Delete `LLM_STEP_TO_STAGE` constant

**Files affected:**
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/infrastructure/LlmCallLogger.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/infrastructure/LlmClient.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/infrastructure/LlmCallLoggingService.java`
- All callers of `LlmClient` methods that log — add `StageKey` parameter

---

## QW5: Extract `sanitizeExceptionMessage` to utility, remove `updateJobStatus` call sites

**Source issue:** #12 (split into two steps)

### QW5a: Extract sanitize to utility

**Problem:** `PipelineStageSupport.sanitizeExceptionMessage()` is a static, pure-function utility with 14 call sites. It's trapped in a class destined for deletion.

**Fix:** Create `ExceptionSanitizer` utility class in `com.lorevault.api.ingestion.infrastructure`:

```java
public final class ExceptionSanitizer {
    private ExceptionSanitizer() {}

    public static String sanitizeMessage(Exception e) {
        // identical body to PipelineStageSupport.sanitizeExceptionMessage()
    }

    public static String safeMessage(Exception e) {
        // consolidate: currently duplicated in IngestionService, IngestionIsolatedLookupService, PipelineStageSupport
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
```

Replace 14 `PipelineStageSupport.sanitizeExceptionMessage(e)` → `ExceptionSanitizer.sanitizeMessage(e)`.

### QW5b: Remove all `stageSupport.updateJobStatus(...)` call sites

**Problem:** 16 call sites calling `PipelineStageSupport.updateJobStatus()` → `IngestionJobService.updateJobStatus()` which is `@Transactional(propagation = REQUIRES_NEW)` containing **only** `log.debug()`. Each call creates an unnecessary `REQUIRES_NEW` transaction. Zero behavioral change — pure dead code.

**Call sites (16 total):**

| File | Lines | Count |
|------|-------|-------|
| `SceneDetectionHandler.java` | 177, 229, 256 | 3 |
| `ChunkingHandler.java` | 115, 147 | 2 |
| `EmbeddingHandler.java` | 102, 120 | 2 |
| `ChapterIndividualResolutionHandler.java` | 80 | 1 |
| `BookIndividualReductionHandler.java` | 92 | 1 |
| `ChapterCollectiveResolutionHandler.java` | 81 | 1 |
| `BookCollectiveReductionHandler.java` | 92 | 1 |
| `ChapterLocationResolutionHandler.java` | 79 | 1 |
| `BookLocationReductionHandler.java` | 90 | 1 |
| `ChapterObjectResolutionHandler.java` | 81 | 1 |
| `ChapterEventEmbeddingHandler.java` | 102 | 1 |
| `BookObjectReductionHandler.java` | 92 | 1 |

**Fix:** Delete each line. They produce `log.debug(...)` only — no side effects.

**Note:** Do NOT delete `PipelineStageSupport.java` yet (that's Phase 2, issue #12 full removal). This QW just removes the dead call sites and extracts the utility.

---

## QW6: Rename `analyzeChapterTriadsWithIndividuals` → `analyzeChapterTriads`

**Source issue:** #17

**Problem:** Method name implies individuals-only but handles all 6 entity types. The dead wrapper `analyzeChapterTriads` at line 301 has zero production callers.

**Fix:**
1. Rename both overloads (lines 152, 157) `analyzeChapterTriadsWithIndividuals` → `analyzeChapterTriads`
2. Delete the dead wrapper at line 301
3. Update all references

**Blast radius:** 22 files (2 production + 3 test + 17 doc/reference)

| File | Change |
|------|--------|
| `SceneRelationshipAnalysisService.java` | Rename 2 overloads, delete dead wrapper (line 301) |
| `SceneDetectionHandler.java` | Update 2 call sites (lines 226, 336) |
| `SceneDetectionHandlerTest.java` | Update 10 mock/verify calls |
| `IndividualResolutionIT.java` | Update 2 mock calls |
| `SceneRelationshipAnalysisServiceTest.java` | Update 1 direct call |
| ~5 doc files | Update references |

The 2-arg overload delegates to the 3-arg overload with a no-op callback — verify this is still the desired API after rename. If the no-op callback patterns are identical, consider consolidating.

---

## QW7: Rename `replayBoundaryTemporalProjection` → `enrichCrossChapterTemporalEdges`

**Source issue:** #22

**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/scene/SceneDetectionHandler.java`

**Problem:** Three jargon words in the method name: "replay" (implies re-running, but it's initial enrichment), "boundary" (vague — which boundary?), "projection" (SDN implementation jargon).

**What it actually does:** Takes structurally-created cross-chapter `NEXT_IN_READING_ORDER` edges and enriches them with typed temporal semantics.

**Fix:** Rename at line 304 (definition) + line 246 (call site). Pure rename — 1 file, 0 behavior change.

---

## Sequencing

Execute in order: QW1 → QW2 → QW3 → QW4 → QW5 → QW6 → QW7

Each is independent. Can be done in a single PR.

**Estimated effort:** ~30 minutes
