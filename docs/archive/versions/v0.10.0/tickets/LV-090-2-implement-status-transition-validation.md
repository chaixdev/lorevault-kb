# LV-090-2 — Implement Status Transition Validation [refactor]

**Status:** NOT STARTED

## Context

- Current `IngestionJobService.updateJobStatus()` accepts any status transition
- No validation of state machine rules (e.g., can't go from `COMPLETE` to `CHUNKING`)
- See state diagram in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Invalid transitions can corrupt job state (e.g., manual retry logic jumping backwards)
- No enforcement of canonical pipeline stages
- Debugging difficult when jobs have impossible status histories
- Risk of concurrent updates putting job in inconsistent state

## Proposal

- Add `isValidTransition(from, to)` method to `IngestionStatus`
- Implement state transition validation in `IngestionJobService.updateJobStatus()`
- Throw `InvalidStatusTransitionException` (new) for illegal transitions
- Allow idempotent updates (same status → same status)

## Scope

- Create `InvalidStatusTransitionException` in domain/exceptions
- Add static `isValidTransition(IngestionStatus from, IngestionStatus to)` to `IngestionStatus`
- Update `IngestionJobService.updateJobStatus()` to validate before persisting
- Document valid transition graph in `IngestionStatus` JavaDoc

### Valid Transition Rules

```
QUEUED → PREPROCESSING_STARTED, FAILED
PREPROCESSING_STARTED → SCENE_SEGMENTATION, FAILED
SCENE_SEGMENTATION → SCENE_TRIAD_ANALYSIS, FAILED
SCENE_TRIAD_ANALYSIS → CHUNKING, FAILED
CHUNKING → EMBEDDING, FAILED
EMBEDDING → PERSISTING_DATA, FAILED
PERSISTING_DATA → COMPLETE, FAILED
COMPLETE → (none, terminal)
FAILED → (none, terminal)

Any status → Same status (idempotent)
```

## Out of Scope

- Retry logic (different from status transition validation)
- Event-driven handlers (LV-090-5 through LV-090-9)
- Optimistic locking with `@Version` (future enhancement)
- Reverting failed jobs to earlier states (requires manual intervention)

## Technical Notes

### Valid Transition Rules

The following transitions should be enforced:

- `QUEUED` → `PREPROCESSING_STARTED`, `FAILED`
- `PREPROCESSING_STARTED` → `SCENE_SEGMENTATION`, `FAILED`
- `SCENE_SEGMENTATION` → `SCENE_TRIAD_ANALYSIS`, `FAILED`
- `SCENE_TRIAD_ANALYSIS` → `CHUNKING`, `FAILED`
- `CHUNKING` → `EMBEDDING`, `FAILED`
- `EMBEDDING` → `PERSISTING_DATA`, `FAILED`
- `PERSISTING_DATA` → `COMPLETE`, `FAILED`
- `COMPLETE` → (none, terminal)
- `FAILED` → (none, terminal)
- Any status → Same status (idempotent)

### Exception Requirements

- New exception type needed for invalid transitions
- Should include job ID and both statuses (from/to) for debugging
- Should provide clear error message for operators

### Validation Integration

- `IngestionJobService.updateJobStatus()` must validate before persisting
- Idempotent transitions (same → same) should be allowed
- Terminal states cannot transition to any other state

## Acceptance Criteria

- [ ] `InvalidStatusTransitionException` created with jobId, from, to fields
- [ ] `IngestionStatus.isValidTransition()` implemented with complete transition map
- [ ] `IngestionJobService.updateJobStatus()` validates before persisting
- [ ] Idempotent transitions (same → same) allowed
- [ ] Terminal state transitions rejected (COMPLETE/FAILED → anything)
- [ ] Exception message includes job ID and both statuses for debugging
- [ ] JavaDoc on `IngestionStatus` documents valid transition graph

## Quality Gates

- [ ] Build passes
- [ ] All existing tests pass (may need updates for new exception)
- [ ] Unit tests for `isValidTransition()` cover all enum values
- [ ] Integration test verifies exception thrown for invalid transition
- [ ] JaCoCo coverage >85%

## Testing Strategy

### Unit Tests

- Test valid forward transitions are allowed
- Test idempotent transitions (same → same) are allowed
- Test terminal states reject all transitions
- Test backward transitions are rejected
- Test exception contains correct job ID and status information

### Integration Tests

- Verify validation enforced in actual job status updates
- Test concurrent status updates behavior (if applicable)

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md` (state diagram)
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-1 (new statuses must exist)
- **Blocks:** LV-090-5 through LV-090-9 (handlers rely on valid transitions)

---

**Estimated Effort:** 2-3 hours  
**Dependencies:** LV-090-1  
**Blocks:** LV-090-5 through LV-090-9
