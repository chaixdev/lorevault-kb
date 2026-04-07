# LV-090-15 — Add Event Replay Capability [refactor]

**Status:** NOT STARTED

## Context

- Event-driven architecture enables event replay for recovery
- Failed jobs currently require manual intervention
- Need ability to retry from specific stage
- See recovery strategy in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Failed jobs require full re-submission
- Can't retry from point of failure
- Wastes resources re-processing successful stages
- Manual recovery process error-prone

## Proposal

- Add admin endpoint to republish events for failed jobs
- Support replay from specific stage
- Validate idempotency before replay
- Log all replay operations for audit

## Scope

### Replay Functionality

1. **Find Failed Jobs:** Query jobs in FAILED status
2. **Identify Failure Point:** Determine which stage failed
3. **Reconstruct Event:** Build appropriate event from job/database state
4. **Republish Event:** Send event to resume pipeline
5. **Audit Log:** Record replay for troubleshooting

### API Endpoint

- `POST /api/admin/ingestion/replay/{jobId}`
- Query parameter: `fromStage` (optional, default to failure point)
- Returns: Replay status and new event ID
- Requires admin authentication

## Out of Scope

- Automatic retry (separate from manual replay)
- Event sourcing infrastructure
- Full event history storage
- Replay of successful jobs (no use case)
- Bulk replay operations

## Technical Notes

### Idempotency Requirements

- Handlers must be idempotent (check current state before processing)
- Verify replay won't create duplicate data
- Validate job status allows replay (FAILED only)
- Check stage already completed before replay

### Event Reconstruction

- Query database for current job state
- Determine which entities exist (scenes, chunks, embeddings)
- Build event with correct IDs and context
- Validate event payload before publishing

### Safety Checks

- Prevent replay of running jobs
- Prevent replay of completed jobs
- Validate stage transition possible
- Rate limit replay requests (prevent abuse)

## Acceptance Criteria

- [ ] Admin endpoint created for job replay
- [ ] Endpoint validates job exists and is FAILED
- [ ] Determines correct stage to resume from
- [ ] Reconstructs appropriate event from database state
- [ ] Publishes event to resume pipeline
- [ ] Audit logs replay operation
- [ ] Returns error for invalid replay requests
- [ ] Documentation explains replay usage

## Quality Gates

- [ ] Build passes
- [ ] Unit tests for replay logic
- [ ] Integration test verifies replay works
- [ ] Security tests verify admin auth required
- [ ] No data duplication from replay

## Testing Strategy

### Unit Tests

- Verify event reconstruction logic
- Verify validation checks
- Verify audit logging
- Test error cases (invalid job, invalid stage)

### Integration Tests

- Create failed job
- Replay from failure point
- Verify pipeline resumes
- Verify job completes successfully
- Verify no duplicate data created

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-10 (pipeline must be active)
- **Related:** Idempotency work in handlers
- **Optional:** This ticket is optional for v0.10.0 but valuable for operations

---

**Estimated Effort:** 4-5 hours  
**Dependencies:** LV-090-10  
**Priority:** Optional (Phase 4)
