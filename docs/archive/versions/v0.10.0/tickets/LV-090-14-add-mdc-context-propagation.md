# LV-090-14 — Add MDC Context Propagation [refactor]

**Status:** NOT STARTED

## Context

- Async event handlers run in different threads
- Current logging lacks correlation across pipeline stages
- Difficult to trace a single job through logs
- See observability requirements in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Log messages from different handlers can't be correlated
- No job ID in thread-local diagnostic context
- Difficult to debug issues spanning multiple stages
- Log aggregation tools can't group related messages

## Proposal

- Add MDC (Mapped Diagnostic Context) propagation to async handlers
- Populate MDC with job ID and chapter ID
- Configure async executor to copy MDC to worker threads
- Enhance log patterns to include MDC values

## Scope

### MDC Fields to Add

- `jobId` - Unique ingestion job identifier
- `chapterId` - Chapter being processed
- `stage` - Current pipeline stage (preprocessing, chunking, etc.)

### Components to Update

- Create `MdcTaskDecorator` to copy MDC across thread boundaries
- Configure in `AsyncConfig` for ingestion executor
- Update handlers to set MDC at start, clear on completion
- Update logging configuration to include MDC fields

## Out of Scope

- Distributed tracing (Spring Cloud Sleuth - future work)
- Request ID propagation from API layer
- Custom log formatters
- Log aggregation infrastructure setup

## Technical Notes

### Task Decorator Pattern

- Implement Spring's `TaskDecorator` interface
- Copy MDC from parent thread to worker thread
- Restore MDC after task execution
- Handle edge cases (null MDC, cleanup failures)

### Handler Integration

- Set MDC values when event received (from event fields)
- Clear MDC in finally block
- Leverage existing `JobContextPort` for correlation IDs

### Logging Configuration

- Update `logback-spring.xml` pattern to include MDC
- Example pattern: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [jobId=%X{jobId}] - %msg%n`
- Ensure MDC values appear in JSON logs if using structured logging

## Acceptance Criteria

- [ ] `MdcTaskDecorator` created and registered with async executor
- [ ] All handlers populate MDC with job ID and chapter ID
- [ ] MDC cleared in finally blocks
- [ ] Logging pattern includes MDC fields
- [ ] Logs show correlation IDs for entire pipeline
- [ ] MDC doesn't leak between requests
- [ ] Documentation explains MDC usage

## Quality Gates

- [ ] Build passes
- [ ] Tests pass
- [ ] Logs show MDC values in integration tests
- [ ] No MDC leakage detected
- [ ] Thread pool doesn't accumulate stale MDC

## Testing Strategy

### Unit Tests

- Verify TaskDecorator copies MDC correctly
- Verify handlers set MDC values
- Verify MDC cleared on completion
- Verify MDC cleared even on exceptions

### Integration Tests

- Submit job and verify MDC appears in all logs
- Verify same job ID throughout pipeline
- Verify different jobs have different MDC
- Grep logs for job ID to see full trace

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-4 (context port), LV-090-5 through LV-090-9 (handlers)
- **Related:** LV-090-13 (metrics), both improve observability
- **Optional:** This ticket is optional for v0.10.0 but highly recommended

---

**Estimated Effort:** 2-3 hours  
**Dependencies:** LV-090-4, LV-090-5, LV-090-6, LV-090-7, LV-090-8, LV-090-9  
**Priority:** Optional (Phase 4)
