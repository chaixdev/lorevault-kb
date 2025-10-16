# Event-Driven Ingestion Refactor (v0.10.0)

**Status:** PLANNED  
**Priority:** P0 - Technical Debt Blocker  
**Target Milestone:** v0.10.0  
**Ticket Prefix:** LV-090-X  
**Target:** Sprint immediately following LV-08X cleanup  
**Supersedes:** LV-086-1, LV-086-2, LV-086-3, LV-086-4 (deferred to post-refactor)

## Executive Summary

Refactor the imperative `IngestionService.processChapter()` orchestrator into event-driven choreography with independent handlers, enforced state machine, and per-stage idempotency. This unblocks the Entity–Claim extraction pipeline and improves reliability, observability, and development velocity.

## Problem: Current Imperative Orchestration

```java
// Current monolith in IngestionService.processChapter()
updateStatus(PREPROCESSING_STARTED);
scenes = executeSceneDetectionStage();      // Pass1 + triad + edges
chunks = executeChunkingStage(scenes);
embeddings = executeEmbeddingStage(chunks);
completeJob();
```

**Issues:**
- Tightly coupled: one method does everything
- Hard to add stages (triage, extraction, aggregation)
- Coarse retry: delete scenes/chunks and re-run entire method
- Limited event usage (only `ChapterIngestionEvent` at entry)
- Status conflation: `EMBEDDING_CHUNKS` used for chunking AND embedding

## Goal: Event-Driven Choreography

```java
// Target: independent handlers react to events
ChapterIngestionEvent
  → SceneSegmentationRequested
  → ScenesSegmented
  → TriadAnalysisRequested
  → TriadAnalysisCompleted
  → ChunkingRequested
  → ChunksPersisted
  → EmbeddingRequested
  → EmbeddingCompleted
  → JobCompleted
```

**Benefits:**
- Handlers testable and deployable independently
- Natural insertion points for new stages (triage, extraction, etc.)
- Per-stage idempotency and retry
- State machine enforcement
- Better observability (per-stage metrics)

## Scope

### In Scope
- Split `EMBEDDING_CHUNKS` into `CHUNKING` and `EMBEDDING` statuses
- Add state transition validation in `IngestionJobService`
- Create event classes for request/result per stage
- Extract 5 handlers:
  - SceneSegmentationHandler
  - TriadAnalysisHandler
  - ChunkingHandler
  - EmbeddingHandler
  - JobCompletionHandler
- Per-handler idempotency checks
- MDC correlation (jobId, chapterId)
- Integration tests for event-driven flow

### Out of Scope
- Persisted event outbox (Phase 2, optional)
- External broker (Kafka/RabbitMQ)
- Entity-Claim extraction stages (separate epic)
- Timeline query endpoints (LV-086-* deferred)
- UI changes

## Architecture

### State Machine (Enforced)

```
QUEUED
  ↓
PREPROCESSING_STARTED
  ↓
SCENE_SEGMENTATION
  ↓
SCENE_TRIAD_ANALYSIS
  ↓
CHUNKING (NEW)
  ↓
EMBEDDING (NEW)
  ↓
COMPLETE | FAILED
```

**Validation:** `IngestionJobService.updateJobStatus()` checks allowed transitions via adjacency map.

### Events

#### Requests (Commands)
```java
SceneSegmentationRequested(UUID jobId, UUID chapterId)
TriadAnalysisRequested(UUID jobId, UUID chapterId)
ChunkingRequested(UUID jobId, UUID chapterId)
EmbeddingRequested(UUID jobId, UUID chapterId)
```

#### Results (Facts)
```java
ScenesSegmented(UUID jobId, UUID chapterId, int sceneCount)
TriadAnalysisCompleted(UUID jobId, UUID chapterId, int edgeCount)
ChunksPersisted(UUID jobId, UUID chapterId, int chunkCount)
EmbeddingCompleted(UUID jobId, UUID chapterId, int embeddedCount)
```

#### Errors
```java
JobFailed(UUID jobId, UUID chapterId, String reason, boolean retryable)
```

### Handler Contract Template

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class XyzHandler {
    
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async("ingestionTaskExecutor")
    public void handle(XyzRequested event) {
        MDC.put("jobId", event.getJobId().toString());
        MDC.put("chapterId", event.getChapterId().toString());
        
        try {
            // 1. Idempotency check
            if (alreadyProcessed(event)) {
                log.info("Already complete, continuing pipeline");
                publishNextEvent(event);
                return;
            }
            
            // 2. Update status
            updateStatus(event.getJobId(), TARGET_STATUS, "Starting XYZ");
            
            // 3. Do work
            Result result = performWork(event);
            
            // 4. Persist outputs
            saveOutputs(result);
            
            // 5. Publish result
            eventPublisher.publishEvent(new XyzCompleted(...));
            
        } catch (RetryableException e) {
            handleRetry(event, e);
        } catch (Exception e) {
            handleFailure(event, e);
        } finally {
            MDC.clear();
        }
    }
}
```

## Handler Details

### 1. SceneSegmentationHandler

**Event In:** `SceneSegmentationRequested`  
**Status:** `SCENE_SEGMENTATION`  
**Work:**
- Call `sceneDetectionPort.detectScenesInText()` (Pass 1 only - no triad)
- Parse XML, localize coordinates
- Persist scenes via `sceneProcessingService.persistDetectedScenes()`
- Create default temporal edges: `defaultTemporalEdgeService.createAllDefaults()`

**Idempotency:** Check `contentPersistencePort.findScenesByChapterId()` returns non-empty  
**Event Out:** `ScenesSegmented(jobId, chapterId, sceneCount)`  
**Next:** Publishes `TriadAnalysisRequested`

**Note:** Fixes current bug where triad analysis runs before scenes are persisted.

### 2. TriadAnalysisHandler

**Event In:** `TriadAnalysisRequested`  
**Status:** `SCENE_TRIAD_ANALYSIS`  
**Work:**
- Load persisted scenes from graph
- Call `triadOrchestrationService.analyzeChapterTriads()`
- Persist temporal edges via `triadEdgePersistenceService.applyTriadAnalyses()`

**Idempotency:** Check for existing TEMPORAL edges between scenes  
**Event Out:** `TriadAnalysisCompleted(jobId, chapterId, edgeCount)`  
**Next:** Publishes `ChunkingRequested`

### 3. ChunkingHandler

**Event In:** `ChunkingRequested`  
**Status:** `CHUNKING` (NEW)  
**Work:**
- Load scenes
- For each scene: `textChunkingService.extractChunks(sceneText)`
- Persist via `contentPersistencePort.addChunksToScene(sceneId, chunks)`

**Idempotency:** Check `contentPersistencePort.countChunksByChapterId() > 0`  
**Event Out:** `ChunksPersisted(jobId, chapterId, chunkCount)`  
**Next:** Publishes `EmbeddingRequested`

### 4. EmbeddingHandler

**Event In:** `EmbeddingRequested`  
**Status:** `EMBEDDING` (NEW)  
**Work:**
- Call `embeddingService.generateEmbeddingsForChapter(chapterId)`

**Idempotency:** Check chunks have embeddings (count chunks with embeddings == total chunks)  
**Event Out:** `EmbeddingCompleted(jobId, chapterId, embeddedCount)`  
**Next:** Publishes `JobCompletionRequested` or handler calls `ingestionJobService.completeJob()`

### 5. JobCompletionHandler (Optional)

**Event In:** `EmbeddingCompleted` (or dedicated `JobCompletionRequested`)  
**Status:** `COMPLETE`  
**Work:**
- Call `ingestionJobService.completeJob(job, chapterId, chapterLength)`

**Idempotency:** Check job status already COMPLETE  
**Event Out:** None (terminal)

## Implementation Tickets

### Phase 1: Foundation (Week 1)

**LV-090-1: Add CHUNKING and EMBEDDING statuses**
- Update `IngestionStatus` enum
- Add `getProgressPercentage()` for new statuses
- Update `IngestionService` to use new statuses where currently using `EMBEDDING_CHUNKS`

**LV-090-2: State transition validation**
- Add `TransitionValidator` with adjacency map
- Inject into `IngestionJobService.updateJobStatus()`
- Throw `IllegalStateTransitionException` on invalid transition
- Add tests for all valid/invalid transitions

**LV-090-3: Event classes**
- Create `com.lorevault.api.event.ingestion` package
- Implement request/result events for all 4 stages
- Extend `ApplicationEvent`, include jobId and chapterId
- Add Lombok @Value and constructors

**LV-090-4: MDC propagation**
- Add `MdcTaskDecorator implements TaskDecorator`
- Configure in `AsyncConfig.ingestionTaskExecutor()`
- Set MDC in event listeners with jobId/chapterId

**Deliverable:** New statuses in use, state machine enforced, events ready, MDC correlation working

### Phase 2: Extract Handlers (Week 2-3)

**LV-090-5: SceneSegmentationHandler**
- Extract Pass 1 logic from `IngestionService.detectAndPersistScenes()`
- Add idempotency check
- Publish `ScenesSegmented` → `TriadAnalysisRequested`
- Unit tests with mocked ports
- Integration test with Testcontainers

**LV-090-6: TriadAnalysisHandler**
- Extract triad logic from `RetryAwareSceneDetectionService`
- Load scenes from persistence (not in-memory)
- Fix ordering: run after scenes persisted
- Publish `TriadAnalysisCompleted` → `ChunkingRequested`
- Tests

**LV-090-7: ChunkingHandler**
- Extract from `IngestionService.executeChunkingStage()`
- Add idempotency check
- Publish `ChunksPersisted` → `EmbeddingRequested`
- Tests

**LV-090-8: EmbeddingHandler**
- Extract from `IngestionService.executeEmbeddingStage()`
- Add idempotency check
- Publish `EmbeddingCompleted` → call completion or publish event
- Tests

**LV-090-9: Update ChapterProcessor**
- Replace `ingestionService.processChapter()` call
- Publish `SceneSegmentationRequested` only
- Keep retry/lookup logic

**Deliverable:** All handlers implemented, tested individually

### Phase 3: Integration & Cutover (Week 3-4)

**LV-090-10: End-to-end integration tests**
- Full pipeline test with real chapter fixtures
- Validate outputs match old orchestrator
- Test idempotency: re-emit events, verify no duplicates
- Test error recovery: inject failures, verify retries

**LV-090-11: Remove old orchestrator**
- Delete `IngestionService.processChapter()` method
- Delete unused helper methods (executeSceneDetectionStage, etc.)
- Update `RetryAwareSceneDetectionService` to remove triad logic (moved to handler)

**LV-090-12: Performance validation**
- Benchmark against old orchestrator (should be < 5% regression)
- Measure per-stage duration
- Validate LLM call counts unchanged

**Deliverable:** Event-driven pipeline is only path, old code deleted, tests green

### Phase 4: Observability (Week 4, Optional)

**LV-090-13: Per-handler metrics**
- Add Micrometer timers: `lorevault.ingestion.handler.duration{handler, status}`
- Add counters: `lorevault.ingestion.handler.invocations{handler, outcome}`
- Add LLM metrics: `lorevault.ingestion.handler.llm.tokens{handler, type}`

**LV-090-14: Structured logging**
- Ensure all handlers log with MDC context
- Add step-level logging for traceability
- Document log format and correlation strategy

**LV-090-15: Idempotency TCK tests**
- Create abstract `HandlerIdempotencyTCK` test base
- Concrete tests per handler: double-delivery, already-complete scenarios
- Validate no duplicate data created

**Deliverable:** Production-ready observability and idempotency guarantees

## Migration Strategy

### Validation Period (Week 3)
- Run event-driven pipeline on staging with real chapter uploads
- Compare outputs to production baseline
- Monitor metrics and logs
- Fix any discrepancies

### Cutover Plan (Week 4)
1. Deploy LV-090-1 to LV-090-9 to staging
2. Validate end-to-end tests pass
3. Deploy to production with feature flag `event-driven.enabled=true`
4. Monitor for 24h, ready to toggle flag if issues
5. After confidence builds (48h+ stable), deploy LV-090-11 to remove old code

### Rollback Plan
- Before LV-090-11, can revert to old orchestrator via code
- After LV-090-11, rollback requires git revert + redeploy

## Success Criteria

- [ ] All handlers pass unit and integration tests
- [ ] State machine enforces valid transitions (ArchUnit rule added)
- [ ] Idempotency tests pass (double-delivery produces same output)
- [ ] Event-driven pipeline completes for existing test fixtures with identical outputs
- [ ] Performance regression < 5%
- [ ] Zero production incidents during cutover
- [ ] MDC correlation visible in all logs
- [ ] Per-stage metrics visible in dashboards

## Future Extensions (Out of This Epic)

### Phase 2: Event Persistence (v0.10.0)
- Add `EventNode` in Neo4j
- Implement outbox pattern with dispatcher
- At-least-once delivery guarantees

### Phase 3: Claim Extraction Pipeline (v1.1.0+)
- Add handlers: TriageHandler, ExtractionHandler, CatalogMappingHandler, etc.
- Leverage same event patterns established here
- See Entity-Claim model doc

## Deferred Tickets

Mark as **DEFERRED** with note "Blocked by event-driven refactor (v0.10.0 milestone)":
- LV-086-1: Spoiler-gated timeline query endpoints
- LV-086-2: Summary NLQ POC endpoint
- LV-086-3: Evidence toggles and response shapes
- LV-086-4: GraphRAG alignment and docs

**Rationale:** Timeline queries depend on stable Event model and should be built on event-driven foundation.

## References

- **Entity-Claim Model:** `docs/development/versions/v0.8.0/research/Entity-Event-Claim-model.md`
- **Existing Event Plan:** `docs/development/refactor/event-driven-architecture-plan.md` (service consolidation context)
- **Scene Detection:** `docs/development/current/processes/scene-detection-specification.md`
- **Triad Orchestration:** `docs/development/current/processes/triad-orchestration.md`
- **Testing Strategy:** `docs/development/current/testing/testing-strategy-v2-concise.md`

## Sign-off

- **Author:** GitHub Copilot (AI pair programmer)
- **Stakeholder:** Project Lead (approval pending)
- **Priority Justification:** Unblocks Entity-Claim extraction, improves reliability, pays down technical debt that slows feature velocity

---

**Next Actions:**
1. Review and approve this plan
2. Create LV-090-1 through LV-090-15 ticket files
3. Update LV-086-* tickets to DEFERRED status
4. Schedule Phase 1 for next sprint
