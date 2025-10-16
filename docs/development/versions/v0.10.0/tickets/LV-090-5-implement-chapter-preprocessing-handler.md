# LV-090-5 — Implement ChapterPreprocessingHandler [refactor]

**Status:** NOT STARTED

## Context

- Event-driven refactor requires stage-specific handlers
- First handler in pipeline: preprocessing chapter after submission
- See handler design in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Current `IngestionService.processChapter()` couples all stages
- No separate handler for preprocessing stage
- Can't independently test or monitor preprocessing logic

## Proposal

- Create `ChapterPreprocessingHandler` listening for `ChapterSubmittedEvent`
- Extract preprocessing logic from `IngestionService` (text validation, normalization)
- Publish `SceneDetectionCompletedEvent` when preprocessing completes
- Update job status to `PREPROCESSING_STARTED` and then `SCENE_SEGMENTATION`

## Scope

### Handler Responsibilities

1. **Listen** for `ChapterSubmittedEvent`
2. **Set context** from event (using `JobContextPort`)
3. **Update status** to `PREPROCESSING_STARTED`
4. **Validate** chapter text (non-empty, reasonable length)
5. **Normalize** text (trim, normalize whitespace)
6. **Detect scenes** (call `SceneDetectionPort`)
7. **Update status** to `SCENE_SEGMENTATION` (or `FAILED` on error)
8. **Publish** `SceneDetectionCompletedEvent`
9. **Clear context** in finally block

### Files to Create

- `ChapterPreprocessingHandler.java` in `adapter/event/handler/`

### Files to Update

- None (yet) - old orchestrator still active until LV-090-10

## Out of Scope

- Removing logic from `IngestionService` (LV-090-10 cutover)
- Retry logic (handled by `RetryAwareSceneDetectionService`)
- Idempotency (future work, needs event deduplication)
- Publishing `ChapterSubmittedEvent` (done in LV-090-10)

## Technical Notes

## Technical Notes

### Handler Responsibilities

The handler should:
1. Listen for chapter submission events
2. Set job context from event data
3. Update job status to preprocessing started
4. Validate chapter text (non-empty, reasonable length)
5. Normalize text (trim, normalize whitespace if needed)
6. Delegate to scene detection port
7. Update job status to scene segmentation
8. Publish scene detection completed event on success
9. Fail job gracefully on errors
10. Clear job context in cleanup

### Integration Requirements

- Use transactional event listener (only process after commit)
- Run asynchronously to avoid blocking publisher
- Use existing thread pool configuration
- Ensure context cleanup even on exceptions (try-finally pattern)

###Error Handling

- Validation errors should fail job immediately with clear message
- Port failures should be caught and job marked as failed
- All errors should be logged with job ID for correlation

## Acceptance Criteria

- [ ] `ChapterPreprocessingHandler` created with all methods
- [ ] Listens for `ChapterSubmittedEvent` with `@TransactionalEventListener(AFTER_COMMIT)`
- [ ] Annotated with `@Async("ingestionTaskExecutor")`
- [ ] Sets job context at start, clears in finally block
- [ ] Updates status to `PREPROCESSING_STARTED`, then `SCENE_SEGMENTATION`
- [ ] Validates chapter text (non-empty, non-null)
- [ ] Normalizes text before passing to scene detection port
- [ ] Calls `sceneDetectionPort.detectScenes()`
- [ ] Publishes `SceneDetectionCompletedEvent` with scene IDs
- [ ] Fails job gracefully on errors with descriptive message
- [ ] Logs INFO for start/completion, ERROR for failures

## Quality Gates

- [ ] Build passes
- [ ] Unit tests pass
- [ ] Integration test verifies event handling
- [ ] JaCoCo coverage >85%
- [ ] No Checkstyle/SpotBugs violations

## Testing Strategy

### Unit Tests

- Verify successful preprocessing flow (mocked dependencies)
- Verify empty/null text fails job with validation error
- Verify scene detection is called with normalized text
- Verify status updates occur in correct sequence
- Verify event published with correct scene IDs
- Verify context is set at start and cleared on completion
- Verify context cleared even when exceptions occur

### Integration Tests

- Verify event triggers preprocessing with actual Spring context
- Verify job status updated correctly in database
- Verify async processing completes within reasonable time

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-3 (events), LV-090-4 (context port)
- **Blocks:** LV-090-10 (cutover needs all handlers ready)

---

**Estimated Effort:** 3-4 hours  
**Dependencies:** LV-090-3, LV-090-4  
**Blocks:** LV-090-10

