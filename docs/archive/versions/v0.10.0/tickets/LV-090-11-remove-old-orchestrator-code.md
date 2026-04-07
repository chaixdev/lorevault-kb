# LV-090-11 — Remove Old Orchestrator Code [refactor]

**Status:** NOT STARTED

## Context

- Event-driven pipeline activated and validated (LV-090-10)
- Old orchestrator code still exists for backward compatibility
- Time to clean up and finalize migration
- See removal strategy in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Duplicate code paths create maintenance burden
- Old orchestrator could be accidentally invoked
- Code complexity from supporting both approaches
- Tech debt accumulating

## Proposal

- Delete `IngestionService.processChapter()` method
- Remove unused stage-specific methods from orchestrator
- Remove feature flag (event-driven is only path)
- Clean up any orphaned code

## Scope

### Code to Remove

1. `IngestionService.processChapter()` - main orchestrator method
2. Stage-specific methods: `executeSceneDetectionStage()`, `executeChunkingStage()`, etc.
3. Old direct service calls that bypass handlers
4. Feature flag configuration and conditional logic
5. Any deprecated helper methods no longer needed

### Files to Update

- `IngestionService.java` - remove orchestrator methods
- `RetryAwareSceneDetectionService.java` - remove triad logic (moved to handler)
- Configuration files - remove feature flag
- Any tests specific to old orchestrator

## Out of Scope

- Service layer changes (services still used by handlers)
- Port interface changes
- Domain model changes
- Database schema changes

## Technical Notes

### Validation Before Deletion

- Confirm event-driven pipeline running in production (if applicable)
- Verify no direct callers of `processChapter()` remain
- Check for any backward compatibility requirements
- Ensure all tests migrated to test handlers

### Risk Mitigation

- Create git tag before deletion for easy rollback
- Keep deletion in dedicated commit for easy revert
- Verify all existing integration tests still pass
- Run full test suite including mutation tests

## Acceptance Criteria

- [ ] `processChapter()` method deleted from `IngestionService`
- [ ] All stage-specific helper methods removed
- [ ] Triad logic removed from `RetryAwareSceneDetectionService`
- [ ] Feature flag removed from configuration
- [ ] No compilation errors after deletion
- [ ] All tests pass (updated to use handlers)
- [ ] No orphaned code remains
- [ ] Code coverage maintains >85%

## Quality Gates

- [ ] Build passes
- [ ] All tests pass (unit, integration, mutation)
- [ ] ArchUnit tests pass
- [ ] JaCoCo coverage >85%
- [ ] No Checkstyle/SpotBugs violations
- [ ] PIT mutation score >80%

## Testing Strategy

### Regression Testing

- Run full integration test suite
- Verify all ingestion scenarios work
- Test error scenarios and recovery
- Validate job status transitions
- Check metrics and logging

### Code Coverage

- Ensure no coverage gaps from deleted code
- Verify dead code detection catches any orphans
- Check for unused imports and dependencies

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-10 (new pipeline must be active)
- **Blocks:** None (final cleanup step)

---

**Estimated Effort:** 2-3 hours  
**Dependencies:** LV-090-10  
**Blocks:** None
