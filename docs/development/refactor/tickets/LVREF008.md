# LVREF008: Create ContentProcessingService Foundation



**Priority**: High  

**Effort**: 1 day  

**Risk**: Medium  

**Phase**: 3 (Consolidate Content Processing)  

**Dependencies**: Phase 2 complete



## Problem Statement



Content processing is split across 7 services (`SceneDetectionService`, `ScenePersistenceService`, `SceneCoordinateLocalizer`, `TextChunkingService`, `ChunkEmbeddingService`, `TriadOrchestrationService`, `SceneDetectionXmlParser`) for one business process: "Convert chapter text into structured, searchable content."



## Current State



```java

// 7 different services for content processing

SceneDetectionService       // 68 lines - calls AI

├── ScenePersistenceService      // 93 lines - saves results  

├── SceneCoordinateLocalizer     // Coordinate calculations

├── SceneDetectionXmlParser      // XML parsing logic

├── TextChunkingService          // Text processing

├── ChunkEmbeddingService        // Vector generation

└── TriadOrchestrationService    // Scene coordination

```



## Target State



```java

@Service

public class ContentProcessingService {

    private final ContentPersistencePort persistencePort;

    private final SceneDetectionPort sceneDetectionPort;

    private final EmbeddingPort embeddingPort;

    

    // Public API - focused business operations

    public void processChapterContent(Chapter chapter) { ... }

    public ProcessingStatus getProcessingStatus(UUID chapterId) { ... }

    

    // Private helpers to avoid god-service (package-private for testing if needed)

    private SceneDetectionResult detectScenes(String text) { ... }

    private List<TextChunk> chunkContent(SceneDetectionResult scenes) { ... }

    private void generateEmbeddings(List<TextChunk> chunks) { ... }

}

```



**Boundary Guidelines**:

- **Scope**: Text → structured content pipeline only

- **Package**: `com.lorevault.api.service.content` (separate from ingestion)

- **Helpers**: Private methods for readability; avoid sub-services

- **Streaming**: Consider streaming for large chapters (batch embeddings, avoid memory spikes)



## Implementation Steps



1. Create new `ContentProcessingService` class

2. Set up basic service structure with port dependencies

3. Register service in Spring configuration

4. Create initial test structure

5. Prepare for gradual migration of processing logic



## Acceptance Criteria



- [ ] New `ContentProcessingService` class created

- [ ] Basic service structure with port dependencies

- [ ] Service registered in Spring configuration

- [ ] Initial tests created

- [ ] No functional changes yet - foundation only



## Files to Modify



**Files to CREATE**:

- `ContentProcessingService.java` - New service foundation

- `ContentProcessingServiceTest.java` - Initial test structure



**Files to UPDATE**:

- Spring configuration classes - Register new service



## Testing Strategy



- Create basic service structure tests

- Prepare test framework for content processing workflows

- No functional testing yet - this is foundation work



## Risk Assessment



**Medium Risk** - Setting up foundation for complex service consolidation.



**Mitigation**:

- Start with basic structure, no functionality migration yet

- Ensure all dependencies are properly configured

- Validate service can be instantiated and basic methods work



