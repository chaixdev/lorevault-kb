# LV-090-10 — Wire Event Publishers and Activate Pipeline [refactor]

**Status:** NOT STARTED

## Context

- All event handlers implemented (LV-090-5 through LV-090-9)
- Handlers ready but not triggered (no events published yet)
- Need to activate event-driven pipeline alongside existing orchestrator
- See cutover strategy in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Handlers exist but aren't being called
- Need to publish events at each stage completion
- Must ensure both pipelines work during transition
- Risk of breaking existing functionality

## Proposal

- Update `ChapterProcessor` to publish `ChapterSubmittedEvent`
- Add event publishing to each handler when stage completes
- Run both pipelines in parallel initially for validation
- Feature flag to switch between old and new pipeline

## Scope

### Event Publishing Points

1. `ChapterProcessor` → publish `ChapterSubmittedEvent` (triggers preprocessing)
2. `ChapterPreprocessingHandler` → publish `SceneDetectionCompletedEvent`
3. `TriadAnalysisHandler` → publish `TriadAnalysisCompletedEvent`
4. `ChunkingHandler` → publish `ChunkingCompletedEvent`
5. `EmbeddingHandler` → publish `EmbeddingCompletedEvent`

### Files to Update

- `ChapterProcessor.java` - switch from calling orchestrator to publishing event
- Potentially add feature flag configuration for gradual rollout

## Out of Scope

- Removing old orchestrator (LV-090-11)
- Performance optimization
- Idempotency guarantees (future work)
- Dead letter queue for failed events

## Technical Notes

### Feature Flag Strategy

- Add `lorevault.ingestion.event-driven.enabled` property
- Default to `false` initially
- When `true`, publish events; when `false`, use old orchestrator
- Allows safe rollback if issues discovered

### Validation Requirements

- Both pipelines should produce identical results
- Compare final data for test chapters
- Monitor for duplicate processing
- Ensure no race conditions

## Acceptance Criteria

- [ ] `ChapterProcessor` publishes `ChapterSubmittedEvent` instead of calling orchestrator
- [ ] Feature flag controls which pipeline is active
- [ ] All handlers publish their completion events
- [ ] Event chain triggers full pipeline execution
- [ ] Integration tests verify end-to-end flow
- [ ] Both pipelines can coexist (no conflicts)
- [ ] Documentation updated with feature flag usage

## Quality Gates

- [ ] Build passes
- [ ] All existing tests pass
- [ ] New end-to-end integration tests pass
- [ ] Manual testing with test chapters succeeds
- [ ] No regressions in existing functionality

## Testing Strategy

### Integration Tests

- Full pipeline test with real chapter data
- Verify all stages execute in correct order
- Verify final data matches expectations (scenes, chunks, embeddings)
- Test error scenarios (LLM failures, validation errors)
- Verify job status progression correct

### Validation Tests

- Run same chapter through both pipelines
- Compare results (should be identical)
- Verify processing duration comparable
- Check for duplicate data

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-5, LV-090-6, LV-090-7, LV-090-8, LV-090-9 (all handlers)
- **Blocks:** LV-090-11 (can't remove old code until new pipeline proven)

---

**Estimated Effort:** 4-6 hours  
**Dependencies:** LV-090-5, LV-090-6, LV-090-7, LV-090-8, LV-090-9  
**Blocks:** LV-090-11
