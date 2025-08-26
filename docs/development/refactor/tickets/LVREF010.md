# LVREF010: Integrate TriadOrchestrationService into EmbeddingService



**Priority**: Medium  

**Effort**: 6 hours  

**Risk**: Medium  

**Phase**: 3 (Consolidate Content Processing)  

**Dependencies**: LVREF009



## Problem Statement



`TriadOrchestrationService` handles semantic analysis (temporal relationship analysis between scenes) that's conceptually part of embedding/semantic processing rather than being a separate service.



## Current State



```java

@Service

public class TriadOrchestrationService {

    // Semantic analysis of scene relationships

    public List<TriadAnalysis> analyzeChapterTriads(UUID jobId, Chapter chapter) { ... }

    

    // Cross-scene temporal analysis 

    // This is semantic processing, not orchestration

}



@Service 

public class EmbeddingService {

    // Vector embeddings

    public int generateEmbeddingsForChapter(UUID chapterId) { ... }

    

    // Could naturally include semantic analysis

}

```



## Target State



```java

@Service

public class EmbeddingService {

    // Vector embeddings

    public int generateEmbeddingsForChapter(UUID chapterId) { ... }

    

    // Semantic analysis (formerly TriadOrchestrationService)

    public List<TriadAnalysis> analyzeChapterTriads(UUID jobId, Chapter chapter) { ... }

    

    // Both are semantic processing - natural fit

}

```



## Implementation Steps



1. Move `analyzeChapterTriads` method to `EmbeddingService`

2. Move supporting types and records (`TriadAnalysis`, etc.)

3. Update dependencies - inject required services into EmbeddingService

4. Update all callers to use EmbeddingService instead

5. Remove `TriadOrchestrationService` class

6. Update tests to reflect new service structure

7. Validate temporal relationship analysis still works



## Acceptance Criteria



- [ ] Triad orchestration methods moved to EmbeddingService

- [ ] All temporal relationship analysis preserved

- [ ] Cross-scene coordination logic maintained

- [ ] Semantic analysis endpoints work identically

- [ ] Service dependencies properly updated

- [ ] Original TriadOrchestrationService deleted



## Files to Modify



**Files to DELETE**:

- `TriadOrchestrationService.java`

- `TriadOrchestrationServiceTest.java`



**Files to UPDATE**:

- `EmbeddingService.java` - Add triad orchestration methods

- Classes that use TriadOrchestrationService - Update to use EmbeddingService

- Integration tests - Update service references

- Controller classes that handle temporal analysis



## Testing Strategy



- Test that all triad analysis functionality is preserved

- Test integration between embedding and semantic analysis

- Test cross-scene temporal relationship analysis

- Verify LLM prompt handling for triad analysis

- Integration tests for complete semantic processing pipeline



## Risk Assessment



**Medium Risk** - Moving complex semantic analysis logic between services.



**Mitigation**:

- Move functionality incrementally

- Preserve all existing method signatures initially

- Thorough testing of temporal relationship analysis

- Validate LLM integration still works correctly

- Test cross-scene coordination edge cases