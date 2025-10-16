# LV-090-1 — Add CHUNKING and EMBEDDING statuses [refactor]

**Status:** NOT STARTED

## Context

- Current `IngestionStatus` enum conflates chunking and embedding under `EMBEDDING_CHUNKS`
- This obscures actual pipeline progress and makes per-stage metrics impossible
- See refactor plan: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Can't distinguish between chunking phase and embedding phase in status updates
- Progress reporting is ambiguous (which step is at 50%?)
- Per-stage observability (duration, errors) requires separate statuses

## Proposal

- Add two new statuses to `IngestionStatus`: `CHUNKING` and `EMBEDDING`
- Update `getProgressPercentage()` to assign distinct progress values
- Replace uses of `EMBEDDING_CHUNKS` in `IngestionService` with appropriate new statuses

## Scope

- Update `IngestionStatus` enum:
  - Add `CHUNKING` with progress ~50%
  - Add `EMBEDDING` with progress ~70%
  - Keep `EMBEDDING_CHUNKS` temporarily for backward compatibility (mark deprecated)
- Update `getProgressPercentage()` method
- Find and replace `EMBEDDING_CHUNKS` usage in `IngestionService`:
  - Chunking stage → `CHUNKING`
  - Embedding stage → `EMBEDDING`
- Update status descriptions to be clear and user-friendly

## Out of Scope

- Event-driven handlers (LV-090-5 through LV-090-9)
- State transition validation (LV-090-2)
- Removing deprecated `EMBEDDING_CHUNKS` (keep for compatibility during migration)

## Technical Notes

### Current State

- Single status `EMBEDDING_CHUNKS` currently used for two distinct stages
- Progress percentage calculation in `IngestionStatus.getProgressPercentage()` needs update
- Usage locations in `IngestionService` for both chunking and embedding stages

### Requirements

- New statuses should have distinct progress percentages (suggest ~50% for chunking, ~70% for embedding)
- Deprecated status must remain backward compatible during migration
- Clear deprecation notice should guide future removal in v0.11.0

## Acceptance Criteria

- [ ] `CHUNKING` status added to enum
- [ ] `EMBEDDING` status added to enum
- [ ] `getProgressPercentage()` returns correct values for new statuses
- [ ] `IngestionService.executeChunkingStage()` uses `CHUNKING` status
- [ ] `IngestionService.executeEmbeddingStage()` uses `EMBEDDING` status
- [ ] `EMBEDDING_CHUNKS` marked deprecated with clear migration note
- [ ] Existing jobs with `EMBEDDING_CHUNKS` still work (no breaking changes)

## Quality Gates

- [ ] Build passes (Maven clean install)
- [ ] All existing tests pass
- [ ] No compilation errors
- [ ] ArchUnit tests pass
- [ ] JaCoCo coverage thresholds maintained

## Testing Strategy

### Unit Tests

- Verify new statuses return correct progress percentages
- Verify deprecated status still works for backward compatibility
- Verify terminal status checks work correctly for all statuses

### Integration Tests

- Existing ingestion integration tests should pass without modification
- Status history queries should show new statuses for new jobs

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Sibling Tickets:** LV-090-2 (state validation), LV-090-3 (events)

---

**Estimated Effort:** 1-2 hours  
**Dependencies:** None  
**Blocks:** LV-090-5 through LV-090-9 (handlers)

