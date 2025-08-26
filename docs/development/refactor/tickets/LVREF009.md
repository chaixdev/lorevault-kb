# LVREF009: Rename ChunkEmbeddingService to EmbeddingService



**Priority**: Medium  

**Effort**: 4 hours  

**Risk**: Low  

**Phase**: 3 (Consolidate Content Processing)  

**Dependencies**: LVREF008



## Problem Statement



`ChunkEmbeddingService` handles more than just chunk embeddings - it's the core semantic processing service. The name is misleading and doesn't reflect its broader responsibilities for all embedding-related operations.



## Current State



```java

@Service

public class ChunkEmbeddingService {

    // Handles chunk embeddings

    public int generateEmbeddingsForChapter(UUID chapterId) { ... }

    

    // But also semantic search

    public List<Document> search(String query, int limit, double threshold) { ... }

    

    // And other embedding operations

    // The name "ChunkEmbedding" is too narrow

}

```



## Target State



```java

@Service

public class EmbeddingService {

    // Same functionality, better name that reflects broader scope

    public int generateEmbeddingsForChapter(UUID chapterId) { ... }

    public List<Document> search(String query, int limit, double threshold) { ... }

    

    // Ready to expand with additional semantic processing capabilities

}

```



## Implementation Steps



1. Rename `ChunkEmbeddingService.java` to `EmbeddingService.java`

2. Update class name and all internal references

3. Update all classes that inject ChunkEmbeddingService

4. Update Spring configuration and component scan

5. Rename test class and update test references

6. Update documentation and comments



## Acceptance Criteria



- [ ] Service renamed to `EmbeddingService`

- [ ] All functionality preserved with identical behavior

- [ ] Class references updated throughout codebase

- [ ] Tests renamed and updated

- [ ] Documentation updated

- [ ] No behavioral changes to existing functionality



## Files to Modify



**Files to RENAME**:

- `ChunkEmbeddingService.java` → `EmbeddingService.java`

- `ChunkEmbeddingServiceTest.java` → `EmbeddingServiceTest.java`



**Files to UPDATE**:

- All classes that inject ChunkEmbeddingService

- Spring configuration files

- Integration tests

- Documentation files

- Import statements



## Testing Strategy



- Verify all existing functionality works identically

- Update test names and references

- Ensure Spring dependency injection still works

- Run integration tests to validate no regressions



## Risk Assessment



**Low Risk** - Simple rename operation with no logic changes.



**Mitigation**:

- Use IDE refactoring tools to ensure all references are updated

- Run comprehensive tests after rename

- Verify Spring context loads correctly
