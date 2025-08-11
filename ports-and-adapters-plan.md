# Ports & Adapters Migration Plan

## Phase 1: Create Missing Ports (Week 1)

### 1.1 AI/ML Service Ports
```java
// application/port/SceneDetectionPort.java
public interface SceneDetectionPort {
    List<SceneWithCoordinates> detectScenes(String chapterText);
    boolean isAvailable();
}

// application/port/EmbeddingPort.java  
public interface EmbeddingPort {
    List<Float> generateEmbedding(String text);
    List<SearchResult> searchSimilar(List<Float> queryEmbedding, int limit, double threshold);
}
```

### 1.2 Event/Notification Port
```java
// application/port/EventPublishingPort.java
public interface EventPublishingPort {
    void publishIngestionEvent(ChapterIngestionEvent event);
    void publishStatusUpdate(StatusUpdateEvent event);
}
```

## Phase 2: Refactor Service Dependencies (Week 2)

### 2.1 Update Services to Use Ports
- `IngestionService` → only depends on ports, not concrete adapters
- `SceneDetectionService` → use `SceneDetectionPort` instead of direct AI client
- `ChunkEmbeddingService` → use `EmbeddingPort` instead of direct AI integration

### 2.2 Clean Package Structure
- Move `graph/` content to `infrastructure/persistence/neo4j/`
- Rename `service/` to `application/service/`
- Create `application/port/` package

## Phase 3: Create Adapters (Week 3)

### 3.1 AI Service Adapters
```java
// infrastructure/ai/openai/OpenAiSceneDetectionAdapter.java
@Component
public class OpenAiSceneDetectionAdapter implements SceneDetectionPort {
    private final SceneDetectionClient client;
    // Implementation details
}

// infrastructure/ai/openai/OpenAiEmbeddingAdapter.java  
@Component
public class OpenAiEmbeddingAdapter implements EmbeddingPort {
    // Implementation details
}
```

### 3.2 Event Publishing Adapter
```java
// infrastructure/messaging/SpringEventPublishingAdapter.java
@Component
public class SpringEventPublishingAdapter implements EventPublishingPort {
    private final ApplicationEventPublisher publisher;
    // Implementation details
}
```

## Phase 4: Clean Up & Testing (Week 4)

### 4.1 Remove Legacy Code
- Delete tombstone repository interfaces
- Remove direct dependencies in services
- Update import statements

### 4.2 Update Tests
- Mock ports in unit tests instead of concrete adapters
- Update integration tests to use new structure
- Verify all services only depend on interfaces

## Benefits After Migration

### ✅ **Better Testability**
- Services only depend on interfaces → easier mocking
- Concrete adapters tested separately
- Clear separation of concerns

### ✅ **Technology Independence**  
- Can swap AI providers without changing business logic
- Can add PostgreSQL alongside Neo4j easily
- Database technology becomes implementation detail

### ✅ **Cleaner Architecture**
- Dependencies point inward (ports → services)
- Business logic isolated from infrastructure
- Configuration centralized in infrastructure layer

## Migration Strategy

### Pragmatic Approach
1. **No Big Bang**: Migrate one service at a time
2. **Backward Compatible**: Keep old structure until migration complete
3. **Test Coverage**: Ensure tests pass at each step
4. **Incremental Value**: Each phase provides immediate benefits

### Example Migration Order
1. `ChunkEmbeddingService` (simplest - currently stubbed)
2. `SceneDetectionService` (medium complexity)
3. `IngestionService` (most complex - central orchestrator)

## Configuration Changes

### New Configuration Structure
```yaml
# application.yml
lorevault:
  ai:
    scene-detection:
      provider: openai  # or 'local', 'mock'
      timeout: 30s
    embedding:
      provider: openai
      model: text-embedding-3-small
  persistence:
    primary: neo4j
    fallback: postgres  # for future hybrid approach
```

This allows runtime switching of implementations via configuration.
