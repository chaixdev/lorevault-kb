# Event-Driven Refactor: Architecture Review & Recommendations

**Role:** Senior Architect Assessment  
**Date:** January 16, 2025  
**Project:** LoreVault Knowledge Base - Ingestion Pipeline Refactor  
**Status:** Recommendations for Immediate Action

## Executive Summary

As requested, I've conducted a comprehensive review of your ingestion pipeline architecture and current LV-08X backlog. The assessment confirms your instinct: **addressing architectural technical debt now is the right strategic decision** and will significantly improve future velocity.

### Key Findings

1. **Current Imperative Orchestration is a Velocity Blocker**
   - Single monolithic method (`IngestionService.processChapter()`) couples all processing stages
   - Hard to add new stages for Entity-Claim extraction pipeline
   - Limited observability and coarse-grained error recovery

2. **Event-Driven Architecture is the Right Pattern**
   - Decouples stages into independent handlers
   - Natural extension points for future pipelines (triage, extraction, aggregation)
   - Per-stage idempotency, metrics, and retry strategies

3. **Timeline Features Should Wait**
   - LV-086-1 through LV-086-4 would be built on unstable foundation
   - Risk of rework when refactor happens anyway
   - Better to build on clean event-driven foundation

### Recommendation

**Defer LV-086-* timeline features and prioritize event-driven refactor (v0.10.0 milestone).** Estimated 3-4 weeks investment with high ROI for:
- Entity-Claim extraction roadmap (v1.1.0+)
- System reliability and observability
- Developer productivity and onboarding
- Reduced production risk

## Detailed Analysis

### Current Architecture Assessment

#### What You Have Today

```java
// Entry point
CommandIngestionController.submitFile()
  → IngestionService.submitChapter()
    → validate + create job
    → publish ChapterIngestionEvent

// Async handler
ChapterProcessor.handleChapterIngestion()
  → IngestionService.processChapter(job, chapter)
    [MONOLITHIC ORCHESTRATOR - 484 lines]
    → executeSceneDetectionStage()
      → Scene detection Pass 1
      → Triad analysis Pass 2 (BEFORE scenes persisted!)
      → Persist scenes
      → Create default temporal edges
    → executeChunkingStage()
      → Extract chunks from scenes
      → Persist chunks
    → executeEmbeddingStage()
      → Generate embeddings
    → ingestionJobService.completeJob()
```

#### Strengths
- Good ports/adapters separation (`SceneDetectionPort`, `ContentPersistencePort`)
- Existing event infrastructure (`ApplicationEventPublisher`)
- Retry logic for LLM calls (`RetryAwareSceneDetectionService`)
- LLM call logging and telemetry

#### Critical Issues

1. **Tight Coupling**
   - Adding a stage (e.g., triage before extraction) requires changing the orchestrator
   - Can't test stages independently without full pipeline setup
   - Hard to reason about failure recovery per-stage

2. **Status Conflation**
   - `EMBEDDING_CHUNKS` used for both chunking AND embedding
   - Obscures actual pipeline progress
   - Makes per-stage metrics impossible

3. **Order Bug**
   - Triad analysis runs BEFORE scenes are persisted
   - Works today because scenes held in memory, but fragile
   - Risk of temporal edges not attaching correctly

4. **Limited Idempotency**
   - Some checks exist (scene count, chunk count)
   - Not formalized: handlers aren't structured for at-least-once delivery
   - Retry strategy is "delete everything and re-run" (coarse)

5. **Observability Gaps**
   - Can't measure per-stage duration, token usage, error rates
   - ThreadLocal correlation fragile across async boundaries
   - Limited structured logging

### Target Event-Driven Architecture

#### Handler-Based Choreography

```java
// Independent handlers, each with clear contract

SceneSegmentationHandler
  Input: SceneSegmentationRequested
  Work: Detect scenes (Pass 1), persist, create default edges
  Output: ScenesSegmented → publish TriadAnalysisRequested
  Idempotency: Check scenes exist
  Status: SCENE_SEGMENTATION

TriadAnalysisHandler
  Input: TriadAnalysisRequested
  Work: Load scenes, analyze triads (Pass 2), persist edges
  Output: TriadAnalysisCompleted → publish ChunkingRequested
  Idempotency: Check temporal edges exist
  Status: SCENE_TRIAD_ANALYSIS

ChunkingHandler
  Input: ChunkingRequested
  Work: Extract chunks, persist
  Output: ChunksPersisted → publish EmbeddingRequested
  Idempotency: Check chunks exist
  Status: CHUNKING (NEW)

EmbeddingHandler
  Input: EmbeddingRequested
  Work: Generate embeddings
  Output: EmbeddingCompleted → complete job
  Idempotency: Check embeddings exist
  Status: EMBEDDING (NEW)
```

#### Benefits

1. **Independent Development**
   - Each handler testable in isolation
   - Clear input/output contracts
   - Easy to mock dependencies

2. **Natural Extension Points**
   - Add TriageHandler before extraction
   - Add ExtractionHandler after embedding
   - No need to change existing handlers

3. **Per-Stage Observability**
   - Measure duration per handler
   - Track LLM tokens per stage
   - Identify bottlenecks and errors

4. **Reliable Retries**
   - Each handler checks if work already done
   - Safe to re-emit events (at-least-once)
   - Granular error recovery

5. **State Machine Enforcement**
   - Valid transitions checked before status updates
   - Prevents illegal state changes
   - Clear audit trail

### Risk Assessment

#### Risks of Refactor

| Risk | Impact | Mitigation |
|------|--------|------------|
| Regression: outputs differ from current | High | Parallel execution period; compare outputs |
| Performance degradation | Medium | Benchmark per-stage; target < 5% regression |
| Complexity: more files/classes | Low | Clear handler template; developer guide |
| Timeline delay for features | Medium | 4 weeks vs. ongoing rework if skip |

#### Risks of NOT Refactoring

| Risk | Impact | Consequence |
|------|--------|-------------|
| Entity-Claim pipeline hard to add | High | Blocks v1.1.0+ roadmap |
| Timeline features built on unstable base | High | Rework when refactor happens anyway |
| Production incidents from coupling | Medium | One stage failure cascades |
| Developer onboarding harder | Medium | Hard to understand monolithic method |
| Technical debt compounds | High | Future changes increasingly expensive |

**Assessment:** Risks of deferring refactor outweigh risks of doing it. Proceed with confidence.

### Velocity Impact Analysis

#### Near-Term (Weeks 1-4)
- **Reduced feature velocity:** Deferring LV-086-* timeline queries
- **Focused effort:** 100% on refactor, no context switching
- **Clear milestone:** Event-driven pipeline working

#### Medium-Term (Weeks 5-12)
- **Accelerated features:** Timeline queries on stable foundation
- **Unlocked roadmap:** Entity-Claim extraction stages ready
- **Reduced bugs:** Better isolation and testing

#### Long-Term (v1.0+)
- **Sustainable pace:** Easy to add stages and features
- **Lower maintenance:** Independent handlers easier to fix
- **Team scaling:** New devs can own handlers independently

**ROI:** 4 weeks investment → 2-3x velocity improvement for next 6 months

## Recommendations

### Immediate Actions (This Week)

1. **Approve Event-Driven Refactor Plan**
   - Review `docs/development/refactor/event-driven-ingestion-refactor-v0100.md`
   - Approve ticket breakdown (LV-090-001 through LV-090-015)
   - Allocate 3-4 weeks for Phases 1-3

2. **Defer Timeline Features**
   - Mark LV-086-1, LV-086-2, LV-086-3, LV-086-4 as DEFERRED
   - Document reasoning (done: `DEFERRED-LV-086-tickets.md`)
   - Communicate timeline to stakeholders

3. **Prioritize Refactor Sprint**
   - Schedule LV-090-001 through LV-090-004 for Week 1
   - Assign lead developer to coordinate
   - Set up daily standups for refactor progress

### Implementation Strategy

#### Phase 1: Foundation (Week 1)
**Goal:** New statuses, state machine, events, MDC correlation

**Critical path:**
- LV-090-001: Add CHUNKING/EMBEDDING statuses
- LV-090-002: State transition validation
- LV-090-003: Event classes
- LV-090-004: MDC propagation

**Success criteria:** Status machine working, events defined, correlation visible in logs

#### Phase 2: Extract Handlers (Week 2-3)
**Goal:** Move logic into independent handlers

**Critical path:**
- LV-090-005: SceneSegmentationHandler (fixes triad-before-persist bug)
- LV-090-006: TriadAnalysisHandler
- LV-090-007: ChunkingHandler
- LV-090-008: EmbeddingHandler
- LV-090-009: Update ChapterProcessor

**Success criteria:** All handlers pass tests, outputs match old orchestrator

#### Phase 3: Integration & Cutover (Week 3-4)
**Goal:** Remove old code, validate production-ready

**Critical path:**
- LV-090-010: End-to-end integration tests
- LV-090-011: Delete old orchestrator
- LV-090-012: Performance validation

**Success criteria:** Event-driven pipeline only path, green tests, < 5% regression

#### Phase 4: Observability (Week 4, Optional)
**Goal:** Production-grade metrics and monitoring

- LV-090-013: Per-handler Micrometer metrics
- LV-090-014: Structured logging
- LV-090-015: Idempotency TCK tests

**Success criteria:** Dashboard shows per-stage metrics, logs correlated via MDC

### Post-Refactor: Resume Timeline Features

After LV-090 Phase 3 complete:
- Resume LV-086-1 through LV-086-4 with stable foundation
- Implement timeline queries as query-side projections over Event nodes
- Add NLQ summarization as handler if needed
- Build GraphRAG alignment on event-driven patterns

## Stakeholder Communication

### For Product/Business

**Message:**
> "We're pausing timeline query features for 4 weeks to fix the underlying ingestion architecture. This investment:
> - Makes future features 2-3x faster to build
> - Reduces production bugs and downtime risk
> - Unblocks the Entity-Claim extraction roadmap (key differentiator)
> 
> Timeline queries will resume Week 5+ with a better foundation and faster delivery."

### For Engineering Team

**Message:**
> "We're refactoring the ingestion orchestrator into event-driven handlers. Benefits:
> - Easier to test and reason about
> - Natural extension points for new stages
> - Better observability and error recovery
> - Foundation for Entity-Claim pipeline
> 
> Focus: LV-090-001 through LV-090-015 over next 3-4 weeks. Clear templates and contracts to follow."

### For QA

**Message:**
> "New event-driven pipeline requires comprehensive testing:
> - Per-handler unit tests (mocked dependencies)
> - Integration tests with Testcontainers
> - End-to-end fixture validation
> - Idempotency tests (double-delivery scenarios)
> - Performance regression tests
> 
> Parallel execution period in Week 3: compare outputs between old and new paths."

## Success Metrics

### Technical Health
- [ ] State machine enforces transitions (ArchUnit rule passes)
- [ ] All handlers pass unit + integration tests
- [ ] Idempotency tests pass (double delivery → same output)
- [ ] Performance regression < 5%
- [ ] Per-stage metrics visible in dashboards
- [ ] MDC correlation in 100% of logs

### Delivery
- [ ] Phase 1 complete Week 1
- [ ] Phase 2 complete Week 3
- [ ] Phase 3 complete Week 4
- [ ] Zero production incidents during cutover
- [ ] Timeline features resume Week 5+

### Velocity
- [ ] New stages (triage, extraction) added in < 1 week each
- [ ] Handler development time < 50% of current orchestrator changes
- [ ] Developer onboarding time reduced by 30%

## Conclusion

Your instinct to prioritize architectural refactor is correct. The current imperative orchestrator is a **high-interest technical debt** that will:
- Block Entity-Claim extraction (core roadmap item)
- Slow timeline feature development
- Increase production risk
- Compound over time

Investing 3-4 weeks now pays off with:
- 2-3x velocity improvement for 6+ months
- Unblocked roadmap (Entity-Claim stages)
- Better reliability and observability
- Sustainable development pace

**Recommendation: APPROVE event-driven refactor (v0.10.0 milestone) as P0 for next sprint.**

---

## Appendix: Ticket Checklist

### Created Documents
- [x] Event-Driven Refactor Plan: `docs/development/refactor/event-driven-ingestion-refactor-v0100.md`
- [x] Deferred Tickets Summary: `docs/development/versions/v0.9.0/planning/DEFERRED-LV-086-tickets.md`
- [x] Updated LV-086-1 status to DEFERRED
- [x] Updated LV-086-2 status to DEFERRED
- [x] Updated LV-086-3 status to DEFERRED
- [x] Updated LV-086-4 status to DEFERRED

### Next: Create LV-090 Tickets
- [ ] LV-090-001 through LV-090-015 ticket files in `docs/development/refactor/tickets/`
- [ ] Update sprint planning with v0.10.0 milestone
- [ ] Assign lead developer and team
- [ ] Schedule kickoff meeting

---

**Prepared by:** GitHub Copilot (Senior Architect Review)  
**For:** LoreVault Project Lead  
**Date:** January 16, 2025
