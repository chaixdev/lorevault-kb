# Event-Driven Architecture Plan: Post-Consolidation

**Phase**: 6 (Post Service Consolidation)  
**Duration**: 2-3 weeks  
**Risk**: Medium-High  
**Goal**: Transform consolidated services into event-driven pipelines

## Prerequisites

This plan assumes **successful completion** of the service consolidation epic (Phases 1-5), resulting in:

```java
// Consolidated service architecture (target of Phases 1-5)
@Service IngestionService           // Complete chapter submission workflow
@Service ContentProcessingService   // Complete text-to-structured-content pipeline  
@Service SearchService             // Content retrieval and search
@Service SystemHealthService       // System monitoring
```

## Vision: Three Independent Pipelines

Your original insight is perfect - the current monolithic processing should be split into **three independent, event-driven pipelines**:

### Pipeline 1: Storage Pipeline
**Purpose**: Get content stored and searchable as quickly as possible  
**Triggers**: `ChapterUploadedEvent`  
**Produces**: `ChapterStoredEvent`

```java
ChapterUploadedEvent → StorageProcessor → ChapterStoredEvent
    ├── Validation & deduplication
    ├── Basic scene detection (LLM pass 1)
    ├── Text chunking for search
    ├── Vector embeddings  
    └── Graph storage (basic structure)
```

### Pipeline 2: Semantic Analysis Pipeline  
**Purpose**: Build rich narrative understanding and timeline  
**Triggers**: `ChapterStoredEvent`  
**Produces**: `SemanticAnalysisCompleteEvent`

```java
ChapterStoredEvent → SemanticAnalysisProcessor → SemanticAnalysisCompleteEvent
    ├── Cross-chapter temporal analysis (LLM pass 2)
    ├── Timeline relationship building
    ├── Event sequencing and ordering
    └── Advanced graph edge creation
```

### Pipeline 3: Entity Profile Pipeline
**Purpose**: Maintain up-to-date entity knowledge and profiles  
**Triggers**: `SemanticAnalysisCompleteEvent`, `EntityMentionDetectedEvent`  
**Produces**: `EntityProfilesUpdatedEvent`

```java
SemanticAnalysisCompleteEvent → EntityProfileProcessor → EntityProfilesUpdatedEvent
    ├── Entity mention extraction
    ├── Entity relationship discovery
    ├── Profile card generation
    └── Cross-reference building
```

## Benefits of Event-Driven Architecture

### Independent Failure and Recovery
```java
// Current: One failure kills entire process
Chapter submission → Scene detection fails → ENTIRE PROCESS FAILS

// Event-driven: Graceful degradation
Chapter submission → Storage complete (user sees content immediately)
                 → Semantic analysis fails (retry independently)
                 → Entity profiles remain stale (non-blocking)
```

### Natural Scaling Points  
```java
// Different pipelines have different resource needs
Storage Pipeline:     CPU intensive (LLM calls, embeddings)
Semantic Pipeline:    Memory intensive (cross-chapter analysis) 
Entity Pipeline:      I/O intensive (graph traversals)
```

### Clear Service Boundaries
Each pipeline becomes a **focused service** that handles complete business capability:

```java
@Service StorageService          // "Make content searchable"
@Service SemanticAnalysisService // "Understand narrative structure"  
@Service EntityProfileService    // "Maintain character/location knowledge"
@Service IngestionCoordinator    // "Track overall progress across pipelines"
```

## Event Design

### Core Event Hierarchy
```java
// Base event class
public abstract class LoreVaultEvent {
    private final UUID eventId;
    private final LocalDateTime timestamp;
    private final String eventType;
}

// Pipeline trigger events
public class ChapterUploadedEvent extends LoreVaultEvent {
    private final UUID chapterId;
    private final UUID bookId; 
    private final String content;
    private final PublicationCoordinates coordinates;
}

public class ChapterStoredEvent extends LoreVaultEvent {
    private final UUID chapterId;
    private final List<UUID> sceneIds;
    private final List<UUID> chunkIds;
    private final int embeddingCount;
}

public class SemanticAnalysisCompleteEvent extends LoreVaultEvent {
    private final UUID chapterId;
    private final List<UUID> temporalEdgeIds;  
    private final List<UUID> eventIds;
}

// Progress tracking events
public class PipelineProgressEvent extends LoreVaultEvent {
    private final UUID chapterId;
    private final PipelineType pipeline;
    private final ProgressStatus status; // STARTED, COMPLETED, FAILED
}
```

### Event Publishing Strategy
```java
@Service
@Transactional
public class IngestionCoordinator {
    private final ApplicationEventPublisher eventPublisher;
    
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
        // Immediate response with job ID
        UUID jobId = createTrackingJob(request);
        
        // Publish event to start processing
        eventPublisher.publishEvent(new ChapterUploadedEvent(
            request.getChapterId(),
            request.getBookId(), 
            request.getContent(),
            request.getCoordinates()
        ));
        
        return new SubmitChapterResponse(jobId, QUEUED);
    }
}
```

## Pipeline Implementation

### Pipeline 1: Storage Processor
```java
@Component
@Transactional
public class StorageProcessor {
    
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleChapterUploaded(ChapterUploadedEvent event) {
        try {
            // Complete storage workflow
            StorageResult result = storageService.processChapterForStorage(
                event.getChapterId(),
                event.getContent(), 
                event.getCoordinates()
            );
            
            // Publish completion event
            eventPublisher.publishEvent(new ChapterStoredEvent(
                event.getChapterId(),
                result.getSceneIds(),
                result.getChunkIds(),
                result.getEmbeddingCount()
            ));
            
        } catch (Exception e) {
            // Publish failure event, enable retry
            eventPublisher.publishEvent(new PipelineFailedEvent(
                event.getChapterId(), 
                STORAGE_PIPELINE,
                e.getMessage()
            ));
        }
    }
}
```

### Pipeline 2: Semantic Analysis Processor
```java
@Component  
@Transactional
public class SemanticAnalysisProcessor {
    
    private final SemanticAnalysisService semanticService;
    private final ApplicationEventPublisher eventPublisher;
    
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleChapterStored(ChapterStoredEvent event) {
        try {
            // Cross-chapter semantic analysis
            SemanticAnalysisResult result = semanticService.analyzeChapterSemantics(
                event.getChapterId(),
                event.getSceneIds()
            );
            
            // Publish completion event
            eventPublisher.publishEvent(new SemanticAnalysisCompleteEvent(
                event.getChapterId(),
                result.getTemporalEdgeIds(),
                result.getEventIds()
            ));
            
        } catch (Exception e) {
            // Independent failure - doesn't block storage pipeline  
            eventPublisher.publishEvent(new PipelineFailedEvent(
                event.getChapterId(),
                SEMANTIC_ANALYSIS_PIPELINE, 
                e.getMessage()
            ));
        }
    }
}
```

### Pipeline 3: Entity Profile Processor
```java
@Component
@Transactional  
public class EntityProfileProcessor {
    
    private final EntityProfileService entityService;
    private final ApplicationEventPublisher eventPublisher;
    
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleSemanticAnalysisComplete(SemanticAnalysisCompleteEvent event) {
        try {
            // Entity profile building
            EntityProfileResult result = entityService.updateEntityProfiles(
                event.getChapterId(),
                event.getTemporalEdgeIds()
            );
            
            // Publish completion event  
            eventPublisher.publishEvent(new EntityProfilesUpdatedEvent(
                event.getChapterId(),
                result.getAffectedEntityIds(),
                result.getUpdatedProfileIds()
            ));
            
        } catch (Exception e) {
            // Independent failure - doesn't block other pipelines
            eventPublisher.publishEvent(new PipelineFailedEvent(
                event.getChapterId(),
                ENTITY_PROFILE_PIPELINE,
                e.getMessage()
            ));
        }
    }
}
```

## Job Status Evolution

### Multi-Pipeline Status Tracking
```java
public enum PipelineStatus {
    NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED, RETRYING
}

public class IngestionJobStatus {
    private UUID jobId;
    private UUID chapterId;
    
    // Pipeline-specific status
    private PipelineStatus storageStatus;
    private PipelineStatus semanticAnalysisStatus;  
    private PipelineStatus entityProfileStatus;
    
    // Overall job status derived from pipeline states
    public OverallStatus getOverallStatus() {
        if (storageStatus == COMPLETED && 
            semanticAnalysisStatus == COMPLETED && 
            entityProfileStatus == COMPLETED) {
            return FULLY_COMPLETE;
        }
        if (storageStatus == COMPLETED) {
            return SEARCHABLE; // User can search content immediately
        }
        if (storageStatus == FAILED) {
            return FAILED;
        }
        return IN_PROGRESS;
    }
}
```

### User Experience Improvements
```java
// Users see immediate value
POST /api/chapters → 202 Accepted (jobId=123)
GET /api/jobs/123  → { status: "SEARCHABLE", storage: "COMPLETED", semantic: "IN_PROGRESS" }

// Content available for search immediately after storage pipeline
POST /api/search → Returns results from stored chunks

// Timeline features available after semantic pipeline  
GET /api/timeline → Timeline data available

// Entity profiles available after entity pipeline
GET /api/entities/character-123 → Rich profile data
```

## Implementation Phases

### Phase 6A: Event Infrastructure (1 week)
1. **Event Class Hierarchy**: Define all event types
2. **Event Publishers**: Update existing services to publish events
3. **Basic Event Listeners**: Create processor skeleton classes
4. **Job Status Evolution**: Multi-pipeline status tracking

### Phase 6B: Storage Pipeline (1 week)  
1. **Extract Storage Logic**: Move from consolidated IngestionService
2. **Storage Event Processing**: Implement StorageProcessor
3. **Storage Service**: Complete storage workflow in focused service
4. **Integration Testing**: End-to-end storage pipeline validation

### Phase 6C: Semantic Analysis Pipeline (1 week)
1. **Extract Semantic Logic**: Move temporal analysis to focused service
2. **Semantic Event Processing**: Implement SemanticAnalysisProcessor  
3. **Cross-Chapter Context**: Build semantic analysis service
4. **Timeline Integration**: Connect to existing timeline features

### Phase 6D: Entity Profile Pipeline (TBD - Future)
1. **Entity Extraction Logic**: Build entity mention detection
2. **Profile Building**: Entity profile card generation
3. **Entity Event Processing**: Implement EntityProfileProcessor
4. **Graph Integration**: Entity relationship management

## Testing Strategy

### Event-Driven Testing Patterns
```java
@Test
class StorageProcessorTest {
    @Mock StorageService mockStorageService;
    @Mock ApplicationEventPublisher mockEventPublisher;
    
    @Test
    void shouldPublishStoredEventAfterSuccessfulStorage() {
        // Test event processing and publishing
        // Focus on business workflow, not internal calls
    }
    
    @Test 
    void shouldPublishFailureEventOnStorageError() {
        // Test error scenarios and proper event publishing
    }
}
```

### Integration Testing  
```java
@SpringBootTest
@Testcontainers
class EventDrivenIntegrationTest {
    
    @Test
    void shouldProcessChapterThroughAllPipelines() {
        // Submit chapter → verify storage event → verify semantic event → verify entity event
        // End-to-end pipeline validation
    }
}
```

## Benefits Realization

### Developer Experience
- **Clear Boundaries**: Each pipeline is focused business capability
- **Independent Development**: Teams can work on different pipelines
- **Easier Testing**: Test complete pipelines, not service interactions
- **Better Debugging**: Clear event trail shows exactly what happened

### System Resilience
- **Graceful Degradation**: Failed pipelines don't block others  
- **Independent Retry**: Each pipeline can retry independently
- **Partial Results**: Users see value even if not all pipelines complete
- **Resource Isolation**: Different pipelines can scale independently

### Future Extensibility  
```java
// Easy to add new pipelines
@TransactionalEventListener
public class NotificationProcessor {
    void handleEntityProfilesUpdated(EntityProfilesUpdatedEvent event) {
        // Send notifications about character updates
    }
}

// Easy to add new event types
public class UserProgressUpdatedEvent extends LoreVaultEvent {
    // Trigger spoiler filtering updates
}
```

This event-driven architecture transforms LoreVault from a monolithic processing system into a **flexible, resilient, pipeline-based architecture** that provides immediate user value while building rich understanding in the background.
