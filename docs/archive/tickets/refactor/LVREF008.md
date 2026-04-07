# LVREF008: Create SceneProcessingService

**Priority**: High  
**Effort**: 1 day  
**Risk**: Medium  
**Phase**: 3 (Consolidate Content Processing)  
**Dependencies**: Phase 2 complete

## Problem Statement

Scene operations are split across 4 tightly-coupled services (`SceneDetectionService`, `ScenePersistenceService`, `SceneCoordinateLocalizer`, `SceneDetectionXmlParser`) that always work together and share scene data structures.

## Current State

```java
// 4 services that always work together
SceneDetectionService        // 68 lines - AI scene detection
├── ScenePersistenceService      // 93 lines - database persistence  
├── SceneCoordinateLocalizer     // Coordinate calculations
└── SceneDetectionXmlParser      // XML parsing logic
```

These services are artificially separated but have tight coupling:

- SceneDetectionService calls SceneDetectionXmlParser to parse LLM responses
- Results always flow to ScenePersistenceService for database operations
- SceneCoordinateLocalizer works on the same scene coordinates
- All services share the same data structures and lifecycle

## Target State

```java
@Service
public class SceneProcessingService {
    private final ContentPersistencePort contentPersistencePort;
    private final SceneDetectionPort sceneDetectionPort;
    
    // Complete scene lifecycle management
    public List<Scene> detectAndPersistScenes(UUID chapterId) { ... }
    public List<Scene> getScenesByChapterId(UUID chapterId) { ... }
    public void deleteScenesByChapterId(UUID chapterId) { ... }
    
    // Private helpers (formerly separate services)
    private List<SceneWithCoordinates> detectScenesInText(String text) { ... }
    private List<SceneWithCoordinates> parseSceneDetectionXml(String xml) { ... }
    private void validateSceneCoordinates(List<SceneWithCoordinates> scenes) { ... }
}
```

## Implementation Steps

1. Create new `SceneProcessingService` class with proper ports
2. Move scene detection logic from `SceneDetectionService`
3. Integrate XML parsing logic as private methods
4. Move persistence logic from `ScenePersistenceService`
5. Integrate coordinate localization logic
6. Update controllers to use new service
7. Remove old services and update tests

## Acceptance Criteria

- [ ] New `SceneProcessingService` handles scene detection, persistence, and coordination
- [ ] XML parsing logic integrated as private methods
- [ ] Scene coordinate localization included
- [ ] All scene-related endpoints work identically
- [ ] Transaction boundaries properly managed
- [ ] All original services deleted and dependencies updated

## Files to Modify

**Files to CREATE**:

- `SceneProcessingService.java` - Unified scene service (~250 lines)
- `SceneProcessingServiceTest.java` - Comprehensive scene tests

**Files to DELETE**:

- `SceneDetectionService.java`
- `ScenePersistenceService.java`
- `SceneCoordinateLocalizer.java`
- `SceneDetectionXmlParser.java`
- Related test files

**Files to UPDATE**:

- Controller classes - Update to use SceneProcessingService
- Integration tests - Validate complete scene workflows

## Testing Strategy

- Test complete scene lifecycle: detection → persistence → retrieval
- Test transaction boundaries and error handling
- Test XML parsing with various LLM response formats
- Test coordinate validation and edge cases
- Integration tests for scene workflow endpoints

## Risk Assessment

**Medium Risk** - Consolidating 4 services with complex logic and transaction boundaries.

**Mitigation**:

- Preserve all existing functionality exactly
- Carefully manage transaction boundaries (detection vs persistence)
- Thorough testing of XML parsing edge cases
- Validate coordinate calculations match existing behavior
