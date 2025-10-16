# v0.10.0 — Event-Driven Ingestion Architecture

**Status:** PLANNED  
**Priority:** P0 - Technical Debt Blocker  
**Estimated Duration:** 3-4 weeks  
**Theme:** Architectural Refactor

## Overview

Version 0.10.0 focuses on refactoring the monolithic `IngestionService.processChapter()` orchestrator into an event-driven pipeline with independent handlers. This foundational work unblocks the Entity-Claim extraction roadmap and significantly improves system reliability, observability, and development velocity.

## Goals

1. **Decouple Processing Stages**
   - Replace imperative orchestration with event-driven choreography
   - Each stage becomes an independent handler with clear contracts
   - Natural extension points for future pipelines (triage, extraction, aggregation)

2. **Improve Reliability**
   - Per-stage idempotency for safe retries
   - State machine with enforced transitions
   - Granular error recovery

3. **Enhance Observability**
   - Per-stage metrics (duration, tokens, errors)
   - MDC correlation for logs
   - Clear audit trail of pipeline progression

4. **Foundation for Entity-Claim Model**
   - Clean insertion points for extraction pipeline stages
   - Event-driven patterns established for future work

## Scope

### Tickets (LV-090-1 through LV-090-15)

#### Phase 1: Foundation (Week 1)
- **LV-090-1:** Add CHUNKING and EMBEDDING statuses
- **LV-090-2:** State transition validation
- **LV-090-3:** Event classes for pipeline stages
- **LV-090-4:** MDC propagation for correlation

#### Phase 2: Extract Handlers (Week 2-3)
- **LV-090-5:** SceneSegmentationHandler
- **LV-090-6:** TriadAnalysisHandler (fix scene-before-triad ordering)
- **LV-090-7:** ChunkingHandler
- **LV-090-8:** EmbeddingHandler
- **LV-090-9:** Update ChapterProcessor

#### Phase 3: Integration & Cutover (Week 3-4)
- **LV-090-10:** End-to-end integration tests
- **LV-090-11:** Remove old orchestrator
- **LV-090-12:** Performance validation

#### Phase 4: Observability (Week 4, Optional)
- **LV-090-13:** Per-handler Micrometer metrics
- **LV-090-14:** Structured logging
- **LV-090-15:** Idempotency TCK tests

## Deferred from v0.9.0

The following timeline query features from v0.9.0 are deferred to post-refactor:
- LV-086-1: Spoiler-gated timeline query endpoints
- LV-086-2: Summary NLQ POC endpoint
- LV-086-3: Evidence toggles and response shapes
- LV-086-4: GraphRAG alignment and docs

**Rationale:** Build timeline features on stable event-driven foundation rather than retrofitting to imperative orchestrator.

## Success Criteria

- [ ] All handlers pass unit and integration tests
- [ ] State machine enforces valid transitions (ArchUnit rule)
- [ ] Idempotency tests pass (double-delivery safe)
- [ ] Performance regression < 5% vs old orchestrator
- [ ] Per-stage metrics visible in dashboards
- [ ] Zero production incidents during cutover

## Documentation

- **Refactor Plan:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture Recommendations:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Deferred Tickets:** `../v0.9.0/planning/DEFERRED-LV-086-tickets.md`

## Migration Path

1. **Week 1:** Foundation work (statuses, state machine, events)
2. **Week 2-3:** Extract handlers, run parallel with old orchestrator
3. **Week 3:** Validation period on staging
4. **Week 4:** Production cutover with feature flag
5. **Week 4+:** Remove old code after 48h stable

## Post-v0.10.0

After successful refactor:
- Resume LV-086-* timeline features
- Begin Entity-Claim extraction stages (v1.1.0+)
- Add optional event persistence (outbox pattern)
- Consider external broker integration (Kafka/RabbitMQ)

---

**Target Release:** TBD (pending sprint planning)  
**Lead:** TBD
