# LV-090-3 — Define Stage-Specific Event Classes [refactor]

**Status:** NOT STARTED

## Context

- Current architecture has single `ChapterIngestionEvent` for entire pipeline
- Event-driven refactor requires fine-grained events for each stage
- See event flow diagram in: `../../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Single event type can't trigger stage-specific handlers
- No way to correlate events with job context across async boundaries
- Missing payload data for idempotent handler logic (e.g., which scenes to chunk?)
- Can't distinguish retry scenarios from initial attempts

## Proposal

- Create 5 new domain event classes in `com.lorevault.api.domain.event.ingestion`:
  - `ChapterSubmittedEvent` (replaces `ChapterIngestionEvent`)
  - `SceneDetectionCompletedEvent`
  - `TriadAnalysisCompletedEvent`
  - `ChunkingCompletedEvent`
  - `EmbeddingCompletedEvent`
- Each event carries job context (`jobId`, `chapterId`) and stage-specific payload
- All events extend abstract `IngestionStageEvent` base class for common fields

## Scope

### Event Classes to Create

**Base Class:**
```java
public abstract class IngestionStageEvent {
    private final UUID jobId;
    private final UUID chapterId;
    private final String publicationId;
    private final String volumeId;
    private final Instant timestamp;
    
    // Constructor, getters, builder
}
```

**Concrete Events:**

1. **ChapterSubmittedEvent**
   - Payload: `String rawText`, `int expectedSceneCount`
   - Triggers: `ChapterPreprocessingHandler`

2. **SceneDetectionCompletedEvent**
   - Payload: `List<UUID> sceneIds`, `int sceneCount`
   - Triggers: `TriadAnalysisHandler`

3. **TriadAnalysisCompletedEvent**
   - Payload: `List<UUID> sceneIds` (scenes ready for chunking)
   - Triggers: `ChunkingHandler`

4. **ChunkingCompletedEvent**
   - Payload: `List<UUID> chunkIds`, `int totalChunks`
   - Triggers: `EmbeddingHandler`

5. **EmbeddingCompletedEvent**
   - Payload: `List<UUID> embeddedChunkIds`, `int vectorDimension`
   - Triggers: Final persistence/completion handler

## Out of Scope

- Event handlers (LV-090-5 through LV-090-9)
- Event publishing logic (stays in service layer for now)
- Event storage/audit trail (future work)
- Dead-letter queue for failed events (LV-090-13)

## Technical Notes

### Event Structure Requirements

**Base Event:**
- Common fields: job ID, chapter ID, publication coordinates, timestamp
- All events should be immutable value objects
- Should support equality comparison for testing

**Stage-Specific Events:**

1. **ChapterSubmittedEvent**
   - Payload: Raw chapter text, expected scene count
   - Triggers: Chapter preprocessing

2. **SceneDetectionCompletedEvent**
   - Payload: List of scene IDs, scene count
   - Triggers: Triad analysis

3. **TriadAnalysisCompletedEvent**
   - Payload: List of scene IDs ready for chunking
   - Triggers: Text chunking

4. **ChunkingCompletedEvent**
   - Payload: List of chunk IDs, total chunk count
   - Triggers: Embedding generation

5. **EmbeddingCompletedEvent**
   - Payload: List of embedded chunk IDs, vector dimension
   - Triggers: Final persistence/completion

### Design Requirements

- Immutability for thread safety
- Defensive copying for collection fields
- Clear naming using domain language (past tense for completed events)
- Serializable for potential future event sourcing

## Acceptance Criteria

- [ ] `IngestionStageEvent` abstract base class created
- [ ] 5 concrete event classes created with proper fields
- [ ] All events immutable (final fields, defensive copies for collections)
- [ ] Builder pattern implemented for each event
- [ ] JavaDoc explains purpose and triggering handler for each event
- [ ] Package structure matches domain-driven design conventions
- [ ] `equals()` and `hashCode()` implemented for all events
- [ ] `toString()` includes all fields for debugging

## Quality Gates

- [ ] Build passes
- [ ] No compilation errors
- [ ] ArchUnit tests pass (events in correct package)
- [ ] Checkstyle/SpotBugs clean
- [ ] JaCoCo coverage N/A (POJOs with minimal logic)

## Testing Strategy

### Unit Tests

- Verify event immutability (collections cannot be modified after construction)
- Verify event equality and hashCode implementation
- Verify all required fields are validated (non-null constraints)
- Verify defensive copying protects internal state

### Integration Tests

- Not required until events are published (LV-090-5+)
- Verify events are serializable and deserializable

## Links

- **Planning:** `../../../refactor/event-driven-ingestion-refactor-v0100.md` (event flow)
- **Architecture:** `../../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** None (pure domain objects)
- **Blocks:** LV-090-5 through LV-090-9 (handlers need events to listen for)

---

**Estimated Effort:** 2-3 hours  
**Dependencies:** None  
**Blocks:** LV-090-5, LV-090-6, LV-090-7, LV-090-8, LV-090-9
