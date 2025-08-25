# LVREF006: Merge Workflow Orchestration



**Priority**: High  

**Effort**: 6 hours  

**Risk**: Medium  

**Phase**: 2 (Consolidate Ingestion Services)  

**Dependencies**: LVREF005



## Problem Statement



`IngestionWorkflowService` (261 lines) just orchestrates calls to other services. This is classic over-abstraction - the workflow IS the business service.



## Current State



```java

IngestionService.processChapter() -> 

  IngestionWorkflowService.processChapter() ->

    SceneDetectionService.detectScenes()

    TextChunkingService.chunkScenes()  

    ChunkEmbeddingService.generateEmbeddings()

```



## Target State



```java

@Service

public class IngestionService {

    // Direct dependencies on real ports

    private final ContentPersistencePort persistencePort;

    private final SceneDetectionPort sceneDetectionPort;

    

    public void processChapter(IngestionJob job, Chapter chapter) {

        // Direct orchestration, no intermediate service layer

        detectScenesAndProcess(chapter);

        generateEmbeddingsForChapter(chapter.getId());  

        updateJobStatus(job.getId(), COMPLETED);

    }

    

    private void detectScenesAndProcess(Chapter chapter) {

        // Scene detection and processing logic

    }

}

```



## Implementation Steps



1. Move workflow orchestration logic into `IngestionService.processChapter()`

2. Change service dependencies to direct port dependencies

3. Keep database transactions short; do not call LLMs inside a transaction

4. Add idempotency key handling for retried requests

5. Use transactional outbox (or event log) if publishing domain events

6. Merge scene coordination and processing logic behind private helpers (avoid god-service)

7. Remove `IngestionWorkflowService` class

8. Update integration tests to test complete ingestion workflow



## Acceptance Criteria



- [ ] No `IngestionWorkflowService` class exists

- [ ] `IngestionService` directly orchestrates complete workflow

- [ ] Dependencies changed from services to real ports

- [ ] All processing steps preserved (scene detection, chunking, embeddings)

- [ ] Event publishing behavior maintained



## Files to Modify



**Files to DELETE**:

- `IngestionWorkflowService.java`

- `IngestionWorkflowServiceTest.java`



**Files to UPDATE**:

- `IngestionService.java` - Add workflow orchestration

- Integration test files - Test complete workflow



## Testing Strategy



- Test complete end-to-end ingestion workflow

- Focus on business behavior rather than service orchestration

- Validate all processing steps occur in correct order

- Ensure event publishing still happens at right times



## Risk Assessment



**Medium Risk** - Complex orchestration logic with multiple external dependencies.



**Mitigation**:

- Preserve all existing processing steps and order

- Comprehensive integration tests for full workflow

- Test event publishing behavior thoroughly

- Avoid long-running operations in transactions; add timeouts and circuit breakers for LLM calls

- Add idempotency and outbox tests to prevent duplicates and lost events



