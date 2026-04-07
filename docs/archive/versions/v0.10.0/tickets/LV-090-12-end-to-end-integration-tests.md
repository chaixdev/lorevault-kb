# LV-090-12 — End-to-End Integration Tests [refactor]

**Status:** NOT STARTED

## Context

- Event-driven pipeline fully implemented
- Need comprehensive testing before declaring refactor complete
- See test strategy in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Individual handler tests don't validate full pipeline integration
- Need to verify event choreography works correctly
- Must validate idempotency and error recovery
- Performance regression risk

## Proposal

- Create comprehensive end-to-end integration test suite
- Test full ingestion pipeline with real chapter fixtures
- Validate outputs match expected results
- Test error scenarios and recovery
- Benchmark performance against baseline

## Scope

### Test Scenarios

1. **Happy Path:** Full chapter ingestion from submission to completion
2. **Idempotency:** Re-processing same chapter produces no duplicates
3. **Error Recovery:** LLM failures, retries, eventual success
4. **Validation Errors:** Invalid input handled gracefully
5. **Partial Failures:** Scene detection succeeds, embedding fails
6. **Performance:** Processing time within acceptable bounds

### Test Infrastructure

- Use Testcontainers for Neo4j
- Mock LLM providers for deterministic testing
- Sample chapter fixtures with known outputs
- Performance baseline measurements

## Out of Scope

- Load testing (many concurrent jobs)
- Stress testing (extremely large chapters)
- Production deployment validation
- Monitoring/alerting setup

## Technical Notes

### Test Data Requirements

- Multiple test chapters with varying characteristics:
  - Short chapter (1-2 scenes)
  - Normal chapter (5-10 scenes)
  - Complex chapter (many characters, locations)
- Known expected outputs for validation

### Assertions

- Correct number of scenes detected
- All triads extracted and persisted
- Chunks generated with correct relationships
- Embeddings have correct dimensions
- Job status progression correct
- No duplicate data
- Processing duration reasonable

### Error Injection

- Simulate LLM API failures
- Simulate database connectivity issues
- Simulate validation errors
- Verify retry logic and job failure handling

## Acceptance Criteria

- [ ] End-to-end test for happy path (full ingestion)
- [ ] Test for idempotency (re-processing safe)
- [ ] Tests for error scenarios with recovery
- [ ] Tests for validation error handling
- [ ] Performance benchmark test
- [ ] All tests use Testcontainers (isolated)
- [ ] Tests deterministic (no flaky behavior)
- [ ] Test documentation explains fixtures and expectations

## Quality Gates

- [ ] All integration tests pass consistently
- [ ] Performance within 5% of baseline
- [ ] No data duplication detected
- [ ] Error recovery works as expected
- [ ] Tests run in CI pipeline

## Testing Strategy

### Test Structure

Each test should:
1. Start with clean database (Testcontainers)
2. Submit chapter via API or event
3. Wait for completion or timeout
4. Validate final state in database
5. Assert job status correct
6. Clean up resources

### Performance Validation

- Measure end-to-end duration
- Compare to baseline (existing orchestrator)
- Allow 5% performance regression
- Document any changes in processing time

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-10 (pipeline must be active)
- **Related:** LV-090-13 (performance metrics will help)

---

**Estimated Effort:** 4-6 hours  
**Dependencies:** LV-090-10  
**Blocks:** None (validation step)
