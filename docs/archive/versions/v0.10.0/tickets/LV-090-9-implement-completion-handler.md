# LV-090-9 — Implement CompletionHandler [refactor]

**Status:** NOT STARTED

## Context

- Event-driven refactor requires handler for final pipeline stage
- Job completion currently handled inline in orchestrator
- See handler design in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Job completion logic embedded in main orchestrator
- No separation between embedding success and job finalization
- Difficult to add post-processing steps

## Proposal

- Create `CompletionHandler` listening for `EmbeddingCompletedEvent`
- Extract job completion logic to independent handler
- Mark job as complete and update final status

## Scope

### Handler Responsibilities

1. Listen for embedding completed events
2. Set job context from event
3. Update job status to persisting data (if applicable)
4. Perform any final validations
5. Mark job as complete
6. Clear job context in cleanup
7. Log completion metrics

### Files to Create

- `CompletionHandler.java` in event handler package

## Out of Scope

- Removing logic from orchestrator (LV-090-11)
- Additional post-processing steps (future work)
- Notifications/webhooks on completion
- Job retention/archival logic

## Technical Notes

### Requirements

- Terminal handler (no outgoing events)
- Should verify all expected data persisted
- Calculate total processing duration
- Log final statistics (scene count, chunk count, embedding count)

### Error Handling

- Missing data should fail job with diagnostic message
- Completion should be idempotent (re-running safe)

## Acceptance Criteria

- [ ] Handler listens for `EmbeddingCompletedEvent` after commit
- [ ] Runs asynchronously using configured thread pool
- [ ] Sets and clears job context properly
- [ ] Updates status to `PERSISTING_DATA` if needed
- [ ] Verifies chapter, scenes, chunks, embeddings all persisted
- [ ] Marks job as `COMPLETE`
- [ ] Logs completion with duration and counts
- [ ] Handles errors gracefully

## Quality Gates

- [ ] Build passes
- [ ] Unit tests pass with mocked dependencies
- [ ] Integration test verifies completion
- [ ] JaCoCo coverage >85%
- [ ] No Checkstyle/SpotBugs violations

## Testing Strategy

### Unit Tests

- Verify successful completion flow
- Verify status updated to COMPLETE
- Verify validation checks run
- Verify context lifecycle
- Verify error handling for missing data

### Integration Tests

- Verify event triggers completion
- Verify job marked COMPLETE in database
- Verify completion metrics logged
- Verify idempotency (completing twice is safe)

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-3 (events), LV-090-4 (context), LV-090-8 (previous handler)
- **Blocks:** LV-090-10 (cutover)

---

**Estimated Effort:** 2-3 hours  
**Dependencies:** LV-090-3, LV-090-4, LV-090-8  
**Blocks:** LV-090-10
