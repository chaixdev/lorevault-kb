# SSE Event Migration — Fix Broken SSE + Delete Dead Events

**Date:** May 24, 2026
**Status:** Ready to execute (bug fix, not structural cleanup)
**Parent:** [Submission Flow Code Quality Cleanup](2026-05-23T1530_submission-flow-cleanup.md) (issue #10a)
**Oracle reviewed:** May 24, 2026 — confirmed live bug, not just cleanup.

The SSE job status streaming (`JobStatusBroadcaster`) is silently broken: it listens for `IngestionEvent` but all handler-published events are now `StageCompletedEvent`. Fixing this and deleting the 12 dead event classes that go with it.

---

## Current State: Broken SSE

### `JobStatusBroadcaster` listens to `IngestionEvent`

```java
@EventListener
void onStatusUpdate(IngestionEvent event) {
    broadcast("status-update", buildPayload(event));
}
```

### `buildPayload()` handles only 4 event types

```java
if (event instanceof ScenesDetectedEvent sceneEvent) { /* ... */ }
else if (event instanceof ChunksCreatedEvent chunkEvent) { /* ... */ }
else if (event instanceof IngestionCompletedEvent completeEvent) { /* ... */ }
else if (event instanceof IngestionFailedEvent failedEvent) { /* ... */ }
```

### Problem: none of these 4 are published anymore

| Event | Publisher (pre-durable-orchestration) | Publisher (current) | Status |
|-------|--------------------------------------|---------------------|--------|
| `ScenesDetectedEvent` | `SceneDetectionHandler` | **None** — publishes `StageCompletedEvent` | Dead |
| `ChunksCreatedEvent` | `ChunkingHandler` | **None** — publishes `StageCompletedEvent` | Dead |
| `IngestionCompletedEvent` | `IngestionCompletionCoordinator` (deleted) | **None** | Dead |
| `IngestionFailedEvent` | `PipelineStageSupport.runStage()` | **None** — `runStage()` has 0 callers | Dead |

Handlers now publish `StageCompletedEvent` via the durable orchestration model. `JobStatusBroadcaster` never subscribed to `StageCompletedEvent`. **SSE streaming has been broken since the durable orchestration migration.**

---

## Fix: Subscribe to `StageCompletedEvent`

### Replace `IngestionEvent` listener with `StageCompletedEvent` listener

```java
@EventListener
void onStageCompleted(StageCompletedEvent event) {
    broadcast("status-update", buildPayload(event));
}

private Map<String, Object> buildPayload(StageCompletedEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventType", "STAGE_COMPLETED");
    payload.put("jobId", event.getJobId());
    payload.put("chapterId", event.getChapterId());
    payload.put("bookId", event.getBookId());
    payload.put("stage", event.getStage().name());
    payload.put("success", event.getResult().success());
    payload.put("summary", event.getResult().summary());
    payload.put("counts", event.getResult().counts());
    payload.put("elapsedMs", event.getResult().elapsedMs());
    return payload;
}
```

### Mapping: `StageKey` → human-readable status

The SSE payload already includes `event.getStage().name()` (e.g., `"SCENE_SEGMENTATION"`). Clients can map these to display strings. No additional mapping table needed in the backend — `StageKey.name()` is already human-readable enough for SSE consumers.

---

## Delete 12 Dead Event Classes

### Confirmed dead (zero production publishers)

| Event class | Test references | Delete |
|-------------|----------------|--------|
| `ScenesDetectedEvent` | 6 test files | ✓ |
| `ChunksCreatedEvent` | 1 test file (EmbeddingHandlerTest) | ✓ |
| `EmbeddingsCompletedEvent` | 0 | ✓ |
| `ChapterIndividualsResolvedEvent` | 2 test files | ✓ |
| `ChapterCollectivesResolvedEvent` | 2 test files | ✓ |
| `ChapterLocationsResolvedEvent` | 2 test files | ✓ |
| `ChapterObjectsResolvedEvent` | 2 test files | ✓ |
| `BookIndividualsReducedEvent` | 0 | ✓ |
| `BookCollectivesReducedEvent` | 0 | ✓ |
| `BookLocationsReducedEvent` | 0 | ✓ |
| `BookObjectsReducedEvent` | 0 | ✓ |
| `BookEventCandidatesGeneratedEvent` | 0 | ✓ |

### NOT deleted (still active)

| Event class | Publisher | Status |
|-------------|-----------|--------|
| `ChapterEventsResolvedEvent` | `ChapterEventAnnRerunService` | Active — see issue #10b |
| `ChapterIngestionEvent` | Verify whether still published | TBD |
| `IngestionCompletedEvent` | Verify whether still published | TBD |
| `IngestionFailedEvent` | `PipelineStageSupport.runStage()` (dead method) | Covered by #12 |

---

## Files Changed

| File | Change |
|------|--------|
| `JobStatusBroadcaster.java` | Replace `IngestionEvent` listener with `StageCompletedEvent` listener. Remove import of `IngestionEvent` and the 4 event classes. |
| `JobStatusBroadcasterTest.java` | Rewrite tests to use `StageCompletedEvent` instead of `ScenesDetectedEvent` |
| 12 event `.java` files in `lorevault-core/src/main/java/com/lorevault/api/ingestion/events/` | Delete |
| ~15 test files referencing deleted events | Update to use `StageCompletedEvent` or stage-based triggers |

### Test files that reference deleted events

All references are constructing events for test setup — replace with `StageCompletedEvent`:

| Test file | Events used | Remedy |
|-----------|-------------|--------|
| `JobStatusBroadcasterTest.java` | `ScenesDetectedEvent` | Use `StageCompletedEvent` with appropriate `StageKey` |
| `SceneDetectionHandlerTest.java` | (already uses mocks, not events) | No change needed |
| `ChunkingHandlerTest.java` | `ScenesDetectedEvent` | Replace with `StageCompletedEvent` for `SCENE_SEGMENTATION` |
| `EmbeddingHandlerTest.java` | `ChunksCreatedEvent` | Replace with `StageCompletedEvent` for `CHUNKING` |
| `ChapterIndividualResolutionHandlerTest.java` | `ScenesDetectedEvent` | Replace with `StageCompletedEvent` |
| `ChapterCollectiveResolutionHandlerTest.java` | `ScenesDetectedEvent` | Replace with `StageCompletedEvent` |
| `ChapterLocationResolutionHandlerTest.java` | `ScenesDetectedEvent` | Replace with `StageCompletedEvent` |
| `ChapterObjectResolutionHandlerTest.java` | `ScenesDetectedEvent` | Replace with `StageCompletedEvent` |
| `ChapterEventResolutionHandlerTest.java` | `ScenesDetectedEvent` | Replace with `StageCompletedEvent` |
| `BookIndividualReductionHandlerTest.java` | `ChapterIndividualsResolvedEvent` | Replace with `StageCompletedEvent` |
| `BookCollectiveReductionHandlerTest.java` | `ChapterCollectivesResolvedEvent` | Replace with `StageCompletedEvent` |
| `BookLocationReductionHandlerTest.java` | `ChapterLocationsResolvedEvent` | Replace with `StageCompletedEvent` |
| `BookObjectReductionHandlerTest.java` | `ChapterObjectsResolvedEvent` | Replace with `StageCompletedEvent` |

---

## Verification

After fix, verify SSE works:

```bash
# Terminal 1: Connect to SSE stream
curl -N http://localhost:18080/api/jobs/stream

# Terminal 2: Submit a chapter
curl -X POST http://localhost:18080/api/ingestion/books/{bookId}/chapters \
  -H "Content-Type: application/json" \
  -d '{"chapterNumber":1,"chapterTitle":"Test","chapterText":"Test content"}'
```

Expected: SSE stream shows `status-update` events for each `StageCompletedEvent` as pipeline stages complete.

---

## Sequencing

**Phase 2 — execute after quick wins.** The `JobStatusBroadcaster` fix is independent of `StageDispatcher` and `PipelineStageSupport` deletion. Fixing SSE early means integration testing of other pipeline changes can use the SSE stream for observability.

**Estimated effort:** ~30 minutes (delete files, rewrite broadcaster, update ~15 test files).
