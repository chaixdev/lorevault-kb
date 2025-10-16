# Deferred LV-08X Tickets - Event-Driven Refactor Priority

**Date:** January 16, 2025  
**Decision:** Defer timeline query/NLQ features to focus on event-driven refactor  
**Rationale:** Architectural technical debt is blocking velocity; resolve foundation before building timeline features

## Deferred Tickets

### LV-086-1: Spoiler-gated timeline query endpoints
**Status:** DEFERRED → Event-driven refactor (v0.10.0 milestone)  
**Original Scope:** Timeline query endpoints for ordered Events with spoiler gating  
**Blocker:** Current imperative orchestration couples stages tightly; adding query endpoints on unstable foundation risks rework  
**Timeline:** Resume after LV-090 Phase 3 (Week 4+)

### LV-086-2: Summary NLQ POC endpoint
**Status:** DEFERRED → Event-driven refactor (v0.10.0 milestone)  
**Original Scope:** POC endpoint for GraphRAG-aligned summarization  
**Blocker:** Requires stable Event model and pipeline; current tightly-coupled flow makes it hard to inject summarization logic cleanly  
**Timeline:** Resume after LV-090 Phase 3 (Week 4+)

### LV-086-3: Evidence toggles and response shapes
**Status:** DEFERRED → Event-driven refactor (v0.10.0 milestone)  
**Original Scope:** Stable DTOs and evidence field toggles for timeline responses  
**Blocker:** Response shapes should be designed against event-driven architecture patterns, not retrofitted to old orchestrator  
**Timeline:** Resume after LV-090 Phase 3 (Week 4+)

### LV-086-4: GraphRAG alignment and docs
**Status:** DEFERRED → Event-driven refactor (v0.10.0 milestone)  
**Original Scope:** Alignment documentation for GraphRAG patterns  
**Blocker:** Event-driven foundation changes architectural patterns; document against stable architecture  
**Timeline:** Resume after LV-090 Phase 4 (Week 5+)

## Rationale for Deferral

### Architectural Debt Impact
The current `IngestionService.processChapter()` orchestrator is a monolithic method that:
- Couples scene detection, chunking, and embedding in one transaction
- Makes it hard to add stages (triage, extraction, aggregation) for Entity-Claim model
- Limits retry granularity and idempotency
- Obscures observability (can't measure per-stage metrics)

### Velocity Impact
Building timeline queries on unstable foundation means:
- Rework when refactor happens anyway
- Harder to test (coupled dependencies)
- Difficult to extend (adding summarization, claim extraction)

### Strategic Decision
Paying down technical debt now:
- Unblocks Entity-Claim extraction pipeline (v1.1.0+ roadmap)
- Improves reliability and observability
- Accelerates future feature development
- Reduces risk of production incidents

## Recommended Approach

### Phase 1-4: Event-Driven Refactor (Weeks 1-4)
- Focus: LV-090-001 through LV-090-015
- Goal: Replace imperative orchestrator with event-driven handlers
- Deliverable: Stable, observable, idempotent pipeline

### Post-Refactor: Timeline Features (Week 5+)
- Revisit LV-086-1 through LV-086-4
- Build on stable event-driven foundation
- Leverage per-stage handlers for clean query integration
- Implement summarization as event-driven handler if appropriate

## Communication Plan

### Stakeholders
- Product: Timeline features delayed 4 weeks for architectural improvement
- Engineering: Clear prioritization and focus on foundational work
- QA: Comprehensive testing of refactored pipeline before feature work

### Messaging
"We're pausing timeline query features to fix the underlying ingestion architecture. This 4-week investment will:
- Make timeline features easier to build correctly
- Unblock the Entity-Claim extraction roadmap
- Improve system reliability and performance
- Reduce future maintenance burden

Timeline queries will resume after the refactor with a better foundation."

## Acceptance Criteria for Resuming LV-086-*

Before resuming deferred tickets:
- [ ] LV-090-001 through LV-090-011 complete (event-driven pipeline working)
- [ ] Integration tests pass for all handlers
- [ ] Performance regression < 5% vs old orchestrator
- [ ] Old orchestrator code removed (processChapter method deleted)
- [ ] Documentation updated (architecture docs, handler guide)
- [ ] Production deployment stable for 48+ hours

## Updated Ticket Status

Update each deferred ticket with:

```markdown
**Status:** DEFERRED

**Reason:** Blocked by event-driven ingestion refactor (v0.10.0 milestone). Timeline query features depend on stable event-driven foundation for:
- Clean handler integration points
- Reliable per-stage observability
- Consistent event-driven patterns

**Resume After:** LV-090 Phase 3 completion (est. Week 4+)

**Reference:** `docs/development/refactor/event-driven-ingestion-refactor-v0100.md`
```

## References

- **Event-Driven Refactor Plan:** `docs/development/refactor/event-driven-ingestion-refactor-v0100.md`
- **Entity-Claim Model:** `docs/development/versions/v0.8.0/research/Entity-Event-Claim-model.md`
- **Original Timeline Plan:** `docs/development/versions/v0.9.0/planning/v0.9.0-scene-to-event-entity-plan.md`

---

**Approved By:** (pending)  
**Date:** (pending)
