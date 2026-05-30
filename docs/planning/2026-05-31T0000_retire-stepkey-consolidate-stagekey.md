# Retire StepKey — Consolidate into StageKey

**Status:** PLANNING  
**Created:** 2026-05-31  
**Context:** `StepKey` predates the `StageDispatcher` refactor. It duplicates `StageKey` (12 of 12 values map 1:1) and creates false distinction between "API-facing step catalog" and "internal pipeline DAG." `StageKey` is now the canonical pipeline vertex identifier everywhere; `StepKey` should be retired and all consumers use `StageKey` directly.

## Problem Statement

Two enums represent the same concept — pipeline vertices — with inconsistent naming:

| StepKey | StageKey |
|---|---|
| `DETECT_SCENES` | `SCENE_SEGMENTATION` |
| `CHUNK` | `CHUNKING` |
| `EMBED` | `EMBEDDING` |
| `CHAPTER_CONSOLIDATE_INDIVIDUALS` | `CHAPTER_INDIVIDUAL_CONSOLIDATION` |
| `CHAPTER_CONSOLIDATE_COLLECTIVES` | `CHAPTER_COLLECTIVE_CONSOLIDATION` |
| `CHAPTER_CONSOLIDATE_LOCATIONS` | `CHAPTER_LOCATION_CONSOLIDATION` |
| `CHAPTER_CONSOLIDATE_OBJECTS` | `CHAPTER_OBJECT_CONSOLIDATION` |
| `CHAPTER_CONSOLIDATE_EVENTS` | `CHAPTER_EVENT_CONSOLIDATION` |
| `BOOK_CONSOLIDATE_INDIVIDUALS` | `BOOK_INDIVIDUAL_CONSOLIDATION` |
| `BOOK_CONSOLIDATE_COLLECTIVES` | `BOOK_COLLECTIVE_CONSOLIDATION` |
| `BOOK_CONSOLIDATE_LOCATIONS` | `BOOK_LOCATION_CONSOLIDATION` |
| `BOOK_CONSOLIDATE_OBJECTS` | `BOOK_OBJECT_CONSOLIDATION` |
| _(absent)_ | `CHAPTER_EVENT_EMBEDDING` |
| _(absent)_ | `BOOK_EVENT_CANDIDATE_GENERATION` |
| _(absent)_ | `INGESTION_COMPLETE` |

The entire StepKey/StepDefinition/StepCatalog/StepResult family adds 4 files and ~50 imports for zero semantic value.

## Files to Delete

| File | Why |
|---|---|
| `orchestration/pipeline/StepKey.java` | Redundant with `StageKey` |
| `orchestration/pipeline/StepDefinition.java` | Wraps `StepKey` — replace with `StageKey` in query controller |
| `orchestration/pipeline/StepCatalog.java` | Catalogs `StepDefinition`s — replace with a method on `StageKey` or inline in query controller |

## File to Rename

| Old | New | Why |
|---|---|---|
| `orchestration/pipeline/StepResult.java` | `orchestration/pipeline/StageResult.java` | Already uses `StageKey` for `stepName` field; the name is the only thing wrong |

`StepResult` field changes:
- `stepName` → `stage` (already typed `StageKey`)
- `stepName` parameter → `stage` in factory methods

## Files to Update

### Core module — `lorevault-core`

**Pipeline contract:**
- `StageOperation.java` — return type `StepResult` → `StageResult`
- `StageDispatcher.java` — all `StepResult.*` factory calls → `StageResult.*`
- `StageCompletedEvent.java` — field type `StepResult` → `StageResult`; import

**Handler implementations (15 files):**
- `Scene.java` (§SceneDetectionHandler) — `StepResult.success/failure/retryableFailure` → `StageResult.*`, import
- `ChunkingHandler.java` — same
- `EmbeddingHandler.java` — same
- `ChapterEventConsolidationHandler.java` — same
- `ChapterEventEmbeddingHandler.java` — same
- `ChapterIndividualConsolidationHandler.java` — same
- `ChapterCollectiveConsolidationHandler.java` (if standalone — may use `*ConsolidationOperation`)
- `ChapterLocationConsolidationHandler.java` — same
- `ChapterObjectConsolidationHandler.java` — same
- `BookIndividualConsolidationHandler.java` — same
- `BookCollectiveConsolidationHandler.java` — same
- `BookLocationConsolidationHandler.java` — same
- `BookObjectConsolidationHandler.java` — same
- `IngestionCompleteHandler.java` — same
- `IngestionPipelineCoordinator.java` — import change only (uses `StepResult` via `StageCompletedEvent.getResult()`)

**Operation interfaces (12 files):**
- `ChunkingOperation.java` — default method return type
- `EmbeddingOperation.java` — same
- `SceneDetectionOperation.java` — same
- `Chapter*ConsolidationOperation.java` (individual, collective, location, object, event) — 5 files
- `Book*ConsolidationOperation.java` (individual, collective, location, object) — 4 files

### Web module — `lorevault-web`

**Command controllers (12 files):**
Each uses `StepKey.*` to pass to `StepExecutionResponse.from()` and `StepEventMapper`:
- `StepExecutionCommandController.java` — `StepKey.DETECT_SCENES` → `StageKey.SCENE_SEGMENTATION`, etc.
- `ChapterIndividualConsolidationCommandController.java` — `StepKey.CHAPTER_CONSOLIDATE_INDIVIDUALS` → `StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION`
- `ChapterCollectiveConsolidationCommandController.java` — same pattern
- `ChapterLocationConsolidationCommandController.java`
- `ChapterObjectConsolidationCommandController.java`
- `BookIndividualConsolidationCommandController.java`
- `BookCollectiveConsolidationCommandController.java`
- `BookLocationConsolidationCommandController.java`
- `BookObjectConsolidationCommandController.java`

**Support classes:**
- `StepEventMapper.java` — drop the `StepKey`→`StageKey` mapping; accept `StageKey` directly. Method signatures change from `publishCompletionEvent(StepKey, UUID, UUID, StepResult)` → `publishCompletionEvent(StageKey, UUID, UUID, StageResult)`.
- `StepExecutionResponse.java` — rename class to `StageExecutionResponse`; parameter type `StepKey` → `StageKey`; import `StageResult` instead of `StepResult`
- `StepQueryController.java` → rename to `StageQueryController.java`; use `StageKey` values directly instead of `StepCatalog`/`StepDefinition`

### Test files

- `IngestionPipelineCoordinatorTest.java` — `StepResult.*` → `StageResult.*`
- `StageDispatcherTest.java` — same
- `JobStatusBroadcasterTest.java` — same
- `ChapterIndividualConsolidationCommandControllerWebMvcTest.java` — same
- `ChapterCollectiveConsolidationCommandControllerWebMvcTest.java` — same
- `ChapterObjectConsolidationCommandControllerWebMvcTest.java` — same

## Implementation Notes

### StepCatalog replacement

`StepCatalog` was a `@Component` that hardcoded a list of `StepDefinition` records for the REST query endpoint. Replace with a static method on `StageKey`:

```java
// On StageKey enum — or inline in the query controller
public static List<StageKey> queryableValues() {
    return List.of(
        SCENE_SEGMENTATION,
        CHUNKING,
        EMBEDDING,
        CHAPTER_INDIVIDUAL_CONSOLIDATION,
        CHAPTER_COLLECTIVE_CONSOLIDATION,
        CHAPTER_LOCATION_CONSOLIDATION,
        CHAPTER_OBJECT_CONSOLIDATION,
        CHAPTER_EVENT_CONSOLIDATION,
        CHAPTER_EVENT_EMBEDDING,
        BOOK_INDIVIDUAL_CONSOLIDATION,
        BOOK_COLLECTIVE_CONSOLIDATION,
        BOOK_LOCATION_CONSOLIDATION,
        BOOK_OBJECT_CONSOLIDATION,
        BOOK_EVENT_CANDIDATE_GENERATION
    );
}
```

`INGESTION_COMPLETE` is excluded — not because it's "internal," but because it's a no-op terminal DAG barrier (`return success("done", 0L)`) with no manual execution value. The other 14 stages have real handlers.

### StepEventMapper simplification

Current flow: `StepKey` → map to `StageKey` → publish event. After retirement, `StageKey` goes directly. The `toStageKey()` mapping method on `StepEventMapper` is deleted.

The `StepEventMapper` class name itself should probably remain (or become `StageEventMapper`) — but the mapper's job of publishing completion events is still needed, it just takes `StageKey` as input now.

### Verifying the mapping

Important to verify the `StepKey`→`StageKey` semantic mapping is correct before deleting. The naming differences (CHUNK→CHUNKING, EMBED→EMBEDDING, CONSOLIDATE→CONSOLIDATION) mean this can't be a simple sed. Each reference must be manually mapped:

```
StepKey.DETECT_SCENES                    → StageKey.SCENE_SEGMENTATION
StepKey.CHUNK                            → StageKey.CHUNKING
StepKey.EMBED                            → StageKey.EMBEDDING
StepKey.CHAPTER_CONSOLIDATE_INDIVIDUALS  → StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION
StepKey.CHAPTER_CONSOLIDATE_COLLECTIVES  → StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION
StepKey.CHAPTER_CONSOLIDATE_LOCATIONS    → StageKey.CHAPTER_LOCATION_CONSOLIDATION
StepKey.CHAPTER_CONSOLIDATE_OBJECTS      → StageKey.CHAPTER_OBJECT_CONSOLIDATION
StepKey.CHAPTER_CONSOLIDATE_EVENTS       → StageKey.CHAPTER_EVENT_CONSOLIDATION
StepKey.BOOK_CONSOLIDATE_INDIVIDUALS     → StageKey.BOOK_INDIVIDUAL_CONSOLIDATION
StepKey.BOOK_CONSOLIDATE_COLLECTIVES     → StageKey.BOOK_COLLECTIVE_CONSOLIDATION
StepKey.BOOK_CONSOLIDATE_LOCATIONS       → StageKey.BOOK_LOCATION_CONSOLIDATION
StepKey.BOOK_CONSOLIDATE_OBJECTS         → StageKey.BOOK_OBJECT_CONSOLIDATION
```

### toUrlSegment() migration

`StepKey.toUrlSegment()` is used by `StepExecutionResponse.from()` to generate the response `step` field. `StageKey` needs this method added (or the response builder handles it differently). Since `StageKey` values are already kebab-case-friendly (`SCENE_SEGMENTATION` → `scene-segmentation`), the same logic works.

## Execution Order

1. Add `toUrlSegment()` to `StageKey` (if not present) and add `queryableValues()` static method
2. Rename `StepResult` → `StageResult` (IDE refactor, catches all references automatically)
3. Rename `StepExecutionResponse` → `StageExecutionResponse` (IDE refactor)
4. Rename `StepQueryController` → `StageQueryController`
5. In `StepEventMapper`: drop `StepKey` parameter, accept `StageKey` directly
6. In all command controllers: replace `StepKey.*` → corresponding `StageKey.*`
7. Replace `StepCatalog` usage in query controller with `StageKey.queryableValues()`
8. Delete `StepKey.java`, `StepDefinition.java`, `StepCatalog.java`
9. Verify: `mvn test` passes
