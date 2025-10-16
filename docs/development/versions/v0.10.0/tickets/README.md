# v0.10.0 Tickets - Event-Driven Ingestion Refactor

This directory contains the individual implementation tickets for the v0.10.0 milestone: Event-Driven Ingestion Architecture Refactor.

## Overview

The v0.10.0 milestone refactors the monolithic ingestion orchestrator into an event-driven architecture with independent stage handlers. This pays down technical debt and enables future enhancements like retry strategies, idempotency, and per-stage observability.

**Planning Document:** `../event-driven-ingestion-refactor-v0100.md`  
**Architecture Recommendations:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`

## Ticket Organization

### Phase 1: Foundation (LV-090-1 to LV-090-4)
Foundation tickets establish the infrastructure needed for event-driven processing.

- **LV-090-1:** Add CHUNKING and EMBEDDING statuses
- **LV-090-2:** Implement status transition validation  
- **LV-090-3:** Define stage-specific event classes
- **LV-090-4:** Create JobContextPort with event-safe correlation

**Goal:** Status granularity, state machine enforcement, event definitions, thread-safe context

### Phase 2: Handlers (LV-090-5 to LV-090-9)
Handler tickets extract each pipeline stage into independent event-driven handlers.

- **LV-090-5:** Implement ChapterPreprocessingHandler
- **LV-090-6:** Implement TriadAnalysisHandler
- **LV-090-7:** Implement ChunkingHandler
- **LV-090-8:** Implement EmbeddingHandler
- **LV-090-9:** Implement CompletionHandler

**Goal:** Independent handlers for each stage, event choreography established

### Phase 3: Integration & Cutover (LV-090-10 to LV-090-12)
Integration tickets activate the new pipeline and remove the old orchestrator.

- **LV-090-10:** Wire event publishers and activate pipeline
- **LV-090-11:** Remove old orchestrator code
- **LV-090-12:** End-to-end integration tests

**Goal:** Event-driven pipeline is the only path, old code removed, comprehensive validation

### Phase 4: Observability (LV-090-13 to LV-090-15) - Optional
Observability tickets enhance monitoring and operational capabilities.

- **LV-090-13:** Add per-handler metrics _(optional)_
- **LV-090-14:** Add MDC context propagation _(optional)_
- **LV-090-15:** Add event replay capability _(optional)_

**Goal:** Production-ready observability, operational tooling, recovery mechanisms

## Dependency Graph

```
LV-090-1 (statuses)
  ↓
LV-090-2 (validation) → LV-090-5 (preprocessing)
  ↓                         ↓
LV-090-3 (events) ────────→ LV-090-6 (triad)
  ↓                         ↓
LV-090-4 (context) ────────→ LV-090-7 (chunking)
                              ↓
                            LV-090-8 (embedding)
                              ↓
                            LV-090-9 (completion)
                              ↓
                            LV-090-10 (activate)
                              ↓
                            LV-090-11 (cleanup)
                              ↓
                            LV-090-12 (validate)
                              
Phase 4 (optional):
LV-090-13 (metrics) ←─── depends on LV-090-5 through LV-090-9
LV-090-14 (MDC) ←──────── depends on LV-090-4, LV-090-5 through LV-090-9
LV-090-15 (replay) ←───── depends on LV-090-10
```

## Ticket Conventions

Each ticket follows a standard structure:

- **Status:** NOT STARTED | IN PROGRESS | BLOCKED | COMPLETE | DEFERRED
- **Context:** Why this work is needed
- **Problem:** What issue we're solving
- **Proposal:** High-level approach (functional goals, not implementation)
- **Scope:** What's included
- **Out of Scope:** What's explicitly excluded
- **Technical Notes:** Key requirements and constraints (no code snippets)
- **Acceptance Criteria:** Checklist of completion requirements
- **Quality Gates:** Build, test, coverage requirements
- **Testing Strategy:** How to validate the work
- **Links:** Related planning docs and dependencies
- **Estimated Effort:** Time estimate
- **Dependencies:** Blocking tickets
- **Blocks:** Tickets blocked by this work

## Implementation Guidelines

1. **Complete Phase 1 first** - Foundation tickets are critical path
2. **Handlers can be parallelized** - LV-090-5 through LV-090-9 are independent
3. **Phase 3 must be sequential** - Activate before removing old code
4. **Phase 4 is optional** - Can be done in v0.10.1 or later

## Success Criteria

The v0.10.0 milestone is complete when:

- [ ] All Phase 1-3 tickets COMPLETE
- [ ] Event-driven pipeline is only ingestion path
- [ ] Old orchestrator code deleted
- [ ] All tests passing (unit, integration, mutation)
- [ ] Code coverage maintained (>85% JaCoCo, >80% PIT)
- [ ] End-to-end integration tests validate full pipeline
- [ ] No performance regression (within 5% of baseline)
- [ ] Documentation updated

## Testing Requirements

All tickets must maintain quality gates:

- **Build:** Maven clean install passes
- **Unit Tests:** All existing + new tests pass
- **Integration Tests:** Testcontainers-based validation
- **Code Coverage:** JaCoCo >85%, PIT >80%
- **Static Analysis:** ArchUnit, Checkstyle, SpotBugs clean

## Related Documentation

- **Milestone Overview:** `../README.md`
- **Refactor Plan:** `../event-driven-ingestion-refactor-v0100.md`
- **Architecture Recommendations:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Deferred Tickets:** `../../refactor/DEFERRED-LV-086-tickets.md`

---

**Last Updated:** 2025-10-16  
**Milestone:** v0.10.0  
**Total Tickets:** 15 (12 required, 3 optional)
