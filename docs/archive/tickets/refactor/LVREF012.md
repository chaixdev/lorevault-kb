# LVREF012: Content Processing Integration Tests

**Priority**: Medium  
**Effort**: 1 day  
**Risk**: Low  
**Phase**: 3 (Consolidate Content Processing)  
**Dependencies**: LVREF008, LVREF010

## Problem Statement

Need integration tests for the refined 3-service content processing architecture to ensure the consolidation preserves all functionality and workflows.

## Current State

After Phase 3 consolidation:

- `SceneProcessingService` - Scene detection, persistence, coordination  
- `TextChunkingService` - Text chunking (independent)
- `EmbeddingService` - Embeddings + semantic analysis

## Target State

Comprehensive integration tests that validate:

1. **Scene Processing Pipeline**: Text → Scene Detection → Persistence → Retrieval
2. **Text Chunking Workflows**: Various text sizes and configurations  
3. **Embedding Pipeline**: Chunks → Embeddings → Semantic Search
4. **Cross-Service Integration**: Scene + Chunk + Embedding workflows
5. **Error Handling**: Failure scenarios and recovery

## Implementation Steps

1. Create `ContentProcessingIntegrationTest` test class
2. Add scene processing pipeline tests
   - Text input → scene detection → database persistence
   - Scene retrieval and coordinate validation
   - Error handling (invalid text, LLM failures)
3. Add text chunking workflow tests
   - Small text (single chunk) scenarios
   - Large text (multiple chunks) scenarios  
   - Configuration parameter validation
4. Add embedding pipeline tests
   - Chunk embedding generation
   - Semantic search functionality
   - Embedding cache behavior
5. Add cross-service integration tests
   - Complete content processing workflows
   - Chapter text → scenes → chunks → embeddings
   - End-to-end search scenarios
6. Add error scenario tests
   - LLM service failures
   - Database connectivity issues
   - Invalid input handling

## Acceptance Criteria

- [ ] Scene detection → persistence → coordination pipeline tested
- [ ] Text chunking workflows validated independently
- [ ] Embedding generation → semantic analysis pipeline tested  
- [ ] Cross-service integration scenarios covered
- [ ] Error handling and edge cases validated
- [ ] Performance regression tests included
- [ ] Complete chapter processing workflows tested

## Files to Modify

**Files to CREATE**:

- `ContentProcessingIntegrationTest.java` - End-to-end workflow tests

**Files to UPDATE**:

- Individual service tests - Ensure focused unit testing
- System integration tests - Validate complete pipelines
- Test configuration - Add integration test profiles

## Testing Strategy

**Integration Test Categories:**

1. **Scene Processing Integration**
   - Complete scene lifecycle testing
   - LLM integration with various response formats
   - Database transaction validation

2. **Text Chunking Integration** 
   - Various text sizes and complexity
   - Configuration parameter validation
   - Memory usage with large texts

3. **Embedding Integration**
   - Batch processing workflows
   - Cache consistency validation  
   - Search accuracy verification

4. **Cross-Service Workflows**
   - Chapter ingestion → complete processing
   - Search scenarios across all content types
   - Performance benchmarking

5. **Error Recovery Testing**
   - External service failures
   - Data corruption scenarios
   - Partial processing recovery

## Risk Assessment

**Low Risk** - Integration testing to validate existing functionality.

**Benefits**:

- Comprehensive validation of service consolidation
- Confidence in refactored architecture
- Documentation of expected workflows
- Performance regression detection
- Error scenario coverage
