# Phase 1 Implementation Tickets: Eliminate Utility Services

**Phase**: 1 of 5  
**Duration**: 1 week  
**Risk**: Low  
**Goal**: Remove fake service boundaries for simple utilities

## Ticket REFACTOR-001-1: Inline HashService into Domain Entities

**Priority**: High  
**Effort**: 2 hours  
**Type**: Refactoring  

### Problem
`HashService` is a 10-line utility that just wraps SHA-256 generation. Creating a service for this adds unnecessary indirection and Spring injection complexity.

### Current State
```java
@Service
public class HashService {
    public String generateSha256Hash(String text) {
        // 10 lines of SHA-256 code
    }
}

// Usage scattered across services
@Autowired HashService hashService;
String hash = hashService.generateSha256Hash(content);
```

### Target State
```java
// Move logic directly into domain entities
public class Chapter {
    public static String generateContentHash(String text) {
        // Same 10 lines, but as static utility method
    }
    
    // Or instance method
    public void updateContentHash() {
        this.contentHash = generateContentHash(this.rawText);
    }
}
```

### Implementation Steps
1. Create `ContentHashUtils` static utility class
2. Move hash generation logic from `HashService`  
3. Update all callers to use static method
4. Remove `HashService` class and Spring bean
5. Update tests to not mock hash generation

### Acceptance Criteria
- ✅ No `HashService` class exists
- ✅ All hash generation uses static utility method  
- ✅ All tests pass with identical behavior
- ✅ No Spring injection of hash functionality

### Files to Modify
- `HashService.java` - DELETE
- `Chapter.java` - Add static hash method
- `IngestionWorkflowService.java` - Update hash calls
- `ChapterValidationService.java` - Update hash calls
- Related test files

---

## Ticket REFACTOR-001-2: Move PromptLoaderService into LLM Adapters

**Priority**: Medium  
**Effort**: 4 hours  
**Type**: Refactoring

### Problem
`PromptLoaderService` is just loading text files from resources. This is infrastructure concern that belongs in the LLM adapters, not as a separate business service.

### Current State
```java
@Service
public class PromptLoaderService {
    public PromptTemplate getSceneDetectionPass1PromptTemplate() {
        // Load file from classpath, return template
    }
}

// LLM services depend on this utility
@Autowired PromptLoaderService promptLoader;
```

### Target State
```java
// Prompt loading becomes part of LLM adapter responsibility
@Component
public class OpenAiSceneDetector implements SceneDetectionPort {
    
    private final PromptTemplate sceneDetectionPrompt;
    
    @PostConstruct
    private void loadPrompts() {
        // Load prompts as part of adapter initialization
        this.sceneDetectionPrompt = loadPromptTemplate("scene-detection.txt");
    }
}
```

### Implementation Steps
1. Move prompt loading logic into LLM adapter classes
2. Make prompt loading part of adapter `@PostConstruct` initialization
3. Update LLM adapters to self-manage their prompt dependencies
4. Remove `PromptLoaderService` class
5. Update configuration to support adapter-level prompt loading

### Acceptance Criteria
- ✅ No `PromptLoaderService` class exists
- ✅ LLM adapters manage their own prompt loading
- ✅ All LLM functionality works identically  
- ✅ Configuration remains clean and testable

### Files to Modify
- `PromptLoaderService.java` - DELETE
- `OpenAiSceneDetector.java` - Add prompt loading
- LLM adapter classes - Self-manage prompts
- Configuration classes - Simplify prompt config

---

## Ticket REFACTOR-001-3: Clean Up Unnecessary Mapper Services

**Priority**: Low  
**Effort**: 2 hours  
**Type**: Refactoring

### Problem
Various small mapper services exist for simple data transformations that could be static methods or inline conversions.

### Current State Analysis
Identify and eliminate services that are just:
- Data transformation utilities
- Simple mapping between DTOs and domain objects
- Single-method services with no external dependencies

### Target State
```java
// Instead of mapper services, use:
// 1. Static utility methods for complex mappings
// 2. Inline conversions for simple mappings  
// 3. Builder patterns in DTOs/domain objects
```

### Implementation Steps
1. Audit existing mapper services
2. Identify services that are just utility functions
3. Convert to static utility methods or inline logic
4. Remove unnecessary service classes
5. Update tests to not mock simple transformations

### Acceptance Criteria
- ✅ No services exist that just do data transformation
- ✅ All mapping logic preserved with identical behavior
- ✅ Tests simplified by removing unnecessary mocks
- ✅ Code is more direct and easier to follow

---

## Phase 1 Testing Strategy

### Test Updates Required
1. **Remove Hash Service Mocks**: Tests should not mock utility functions
2. **Simplify Prompt Loading Tests**: Focus on adapter behavior, not utility loading
3. **Direct Assertion**: Test actual hash values, not just "service was called"

### Integration Testing
1. **End-to-End Verification**: Submit chapter, verify identical hash generation
2. **LLM Integration**: Ensure prompt loading still works in adapters
3. **Performance Testing**: Verify no regression from utility consolidation

## Success Criteria for Phase 1

### Quantitative Metrics
- **Services Removed**: 3+ utility services eliminated
- **Dependency Injection Points**: Reduced by ~5-10 @Autowired annotations
- **Test Complexity**: Fewer mock objects in service tests
- **Code Lines**: Slight reduction through consolidation

### Qualitative Metrics
- **Clearer Dependencies**: Services depend on real external boundaries only
- **Simpler Testing**: Less mocking of internal utilities
- **More Direct Code**: Hash generation and prompt loading more obvious

## Risk Assessment

### Low Risk Factors
- **No Business Logic Changes**: Just moving utility functions
- **Straightforward Replacements**: Simple static method conversions
- **Comprehensive Tests**: Existing behavior fully tested

### Mitigation Strategies
- **Incremental Changes**: One utility service at a time
- **Test-Driven**: Ensure all tests pass before next change
- **Quick Rollback**: Easy to revert if issues found

This phase sets the foundation for larger service consolidations by eliminating the easiest fake boundaries first.