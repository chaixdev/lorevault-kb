# LV-090-13 — Add Per-Handler Metrics [refactor]

**Status:** NOT STARTED

## Context

- Event-driven architecture enables per-stage observability
- Current metrics lack granularity (whole pipeline only)
- Need to measure handler performance independently
- See observability plan in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Can't identify which stage is slow
- No visibility into handler-specific failures
- Difficult to optimize without measurements
- Missing duration and throughput metrics

## Proposal

- Add Micrometer metrics to each handler
- Track duration, invocation count, success/failure rates
- Enable per-stage performance monitoring
- Integrate with existing metrics infrastructure

## Scope

### Metrics to Add

1. **Timer:** `lorevault.ingestion.handler.duration`
   - Tags: handler name, outcome (success/failure)
   - Measures processing time per handler

2. **Counter:** `lorevault.ingestion.handler.invocations`
   - Tags: handler name, outcome
   - Counts handler executions

3. **Counter:** `lorevault.ingestion.handler.events.processed`
   - Tags: handler name, event type
   - Tracks event processing

### Handlers to Instrument

- ChapterPreprocessingHandler
- TriadAnalysisHandler
- ChunkingHandler
- EmbeddingHandler
- CompletionHandler

## Out of Scope

- Custom metrics dashboards (use existing tools)
- Alerting rules (future work)
- Distributed tracing (Sleuth integration - future)
- Business metrics (scene count, token usage - separate ticket)

## Technical Notes

### Instrumentation Pattern

Each handler should:
- Start timer when event received
- Record duration on completion
- Increment success/failure counters
- Tag with handler name and outcome
- Use try-finally for reliable recording

### Integration

- Use existing Micrometer registry
- Export to configured monitoring system (Prometheus/Grafana if available)
- Ensure metrics don't impact performance
- Follow Spring Boot Actuator conventions

## Acceptance Criteria

- [ ] All 5 handlers instrumented with timers
- [ ] Invocation counters added to all handlers
- [ ] Metrics tagged with handler name and outcome
- [ ] Success and failure cases both recorded
- [ ] Metrics exposed via Spring Boot Actuator
- [ ] Metrics follow naming conventions
- [ ] Documentation explains available metrics

## Quality Gates

- [ ] Build passes
- [ ] Tests pass (metrics don't break functionality)
- [ ] Metrics visible in actuator endpoint
- [ ] No performance degradation from metrics
- [ ] Metrics appear in test runs

## Testing Strategy

### Unit Tests

- Verify metrics recorded on success
- Verify metrics recorded on failure
- Verify correct tags applied
- Mock MeterRegistry to avoid test pollution

### Integration Tests

- Verify metrics appear after handler execution
- Verify timer values reasonable
- Verify counter increments correctly

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-5 through LV-090-9 (handlers must exist)
- **Optional:** This ticket is optional for v0.10.0 but highly recommended

---

**Estimated Effort:** 2-3 hours  
**Dependencies:** LV-090-5, LV-090-6, LV-090-7, LV-090-8, LV-090-9  
**Priority:** Optional (Phase 4)
