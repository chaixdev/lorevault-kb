# Readability Refactor Tasklist

> **Goal**: Improve code readability and maintainability by breaking down monolithic services into focused, single-responsibility components following ports & adapters architecture principles.

## Completed ✅
- [x] **ChunkEmbeddingService** - Refactored 300+ line monolithic method into 15+ focused helpers with EmbeddingContext
  - Extracted EmbeddingContext class for shared state
  - Broke down embedding pipeline into clear stages
  - Improved testability and readability
  - All tests passing

## In Progress 🔄
- [x] **IngestionService** - Successfully refactored! 🎉
  - [x] Extract `IngestionJobLifecycleService` (job creation, status updates, completion)
  - [x] Extract `ChapterValidationService` (duplicate detection, content validation)  
  - [x] Extract `IngestionWorkflowService` (orchestrate processing pipeline)
  - [x] Extract `JobQueryService` (job listing, filtering, pagination)
  - [x] Update dependency injection and tests
  - [x] All tests passing - refactor complete!

## Planned Refactors 📋

### High Priority
- [x] **IngestionService** - Successfully completed! 🎉
  - [x] Extract `IngestionJobLifecycleService` (job creation, status updates, completion)
  - [x] Extract `ChapterValidationService` (duplicate detection, content validation)  
  - [x] Extract `IngestionWorkflowService` (orchestrate processing pipeline)
  - [x] Extract `JobQueryService` (job listing, filtering, pagination)
  - [x] Update dependency injection and tests
  - **Results**: Reduced from 500+ line monolithic service to 4 focused services, improved testability and maintainability

### Medium Priority  
- [x] ## Medium Priority Tasks

### 1. ContentIngestionController ✅
- **Status**: COMPLETED ✅ (Aug 15, 2025)
- **Current Issues**: 
  - ~~Controller has ~240 lines with mixed validation, error handling, and business logic~~
  - ~~File validation, coordinate validation, error response generation all embedded in controller~~
  - ~~Poor separation of concerns, hard to test individual validation logic~~

### 2. LlmHealthCheckService ✅
- **Status**: COMPLETED ✅ (Aug 15, 2025)  
- **Original Issues**:
  - ~~Service has ~156 lines with mixed concerns (retry logic, health validation, metrics collection)~~
  - ~~Retry mechanism, health validation, and metrics tracking all embedded in single service~~
  - ~~Poor testability due to mixed responsibilities~~
- **Solution Applied**:
  - Extracted `RetryableHealthChecker` for generic retry mechanism with exponential backoff
  - Extracted `ModelHealthValidator` for core health validation logic
  - Extracted `HealthMetricsCollector` for metrics tracking and diagnostics
  - Refactored main service to thin orchestration layer
  - Used context objects (RetryConfig, ValidationConfig, ModelHealthStatus) for structured data transfer
  - Fixed HealthController references after ModelHealthStatus moved to HealthMetricsCollector
- **Results**: Reduced from 156-line monolithic service to focused orchestration layer with 3 specialized services, all 39 tests passing ✅
- **Refactor Plan**: 
  - ✅ Extract FileUploadValidator service for file type/size validation
  - ✅ Extract CoordinatesBuilder service for PublicationCoordinates creation
  - ✅ Extract ErrorResponseFactory service for standardized error responses  
  - ✅ Extract FileContentExtractor service for file processing
  - ✅ Refactor controller to thin orchestration layer
- **Extracted Services**:
  - `FileUploadValidator` - File validation with ValidationResult context object
  - `CoordinatesBuilder` - Coordinate building and validation with CoordinateValidationResult
  - `ErrorResponseFactory` - Standardized error response generation  
  - `FileContentExtractor` - File content extraction with ContentExtractionResult
- **Lines Reduced**: From ~240 lines to ~95 lines (60% reduction)
- **Testing**: All 39 tests passing ✅ (Build Success confirmed)

### Lower Priority
- [ ] **SceneDetectionXmlParser** - Stage-based processing
  - [ ] Extract `XmlResponseCleaner` (text cleanup and normalization)
  - [ ] Extract `SceneXmlValidator` (structure validation)
  - [ ] Extract `SceneDataExtractor` (DOM to domain object mapping)

- [ ] **TextChunkingService** - Strategy pattern
  - [ ] Create `ChunkingStrategy` interface
  - [ ] Extract `SentenceAwareSlidingWindowStrategy`
  - [ ] Extract `ChunkBoundaryOptimizer`

- [ ] **Configuration Modernization** - Convert to YAML and consolidate properties
  - [ ] Convert `application.properties` to `application.yml` format
  - [ ] Consolidate all custom properties under `lorevault.*` namespace
  - [ ] Organize properties by functional area (e.g., `lorevault.llm.*`, `lorevault.embedding.*`, `lorevault.ingestion.*`)
  - [ ] Add property validation with `@ConfigurationProperties` classes
  - [ ] Update environment-specific overrides (dev, test profiles)

## Chore Tasks 🔧

### High Priority Infrastructure
- [ ] **Better LLM Retry Handling** - Improve resilience for parsing failures
  - [ ] Implement exponential backoff with jitter for LLM API calls
  - [ ] Add graceful degradation for unparseable responses (e.g., retry with different prompt)
  - [ ] Create fallback strategies when scene detection XML parsing fails
  - [ ] Log structured error data for debugging failed LLM interactions

- [ ] **Model Parameter Configuration** - Fine-tune LLM behavior via properties
  - [ ] Add `temperature` configuration for creativity control
  - [ ] Add `top-P` (nucleus sampling) parameter configuration
  - [ ] Create model-specific parameter profiles in application.yml
  - [ ] Allow runtime parameter adjustment without restart

- [ ] **Multi-Provider LLM Configuration** - Support different models for different tasks  
  - [ ] Replace Spring AI auto-configuration with custom `@Configuration` classes
  - [ ] Create separate `ChatClient` beans for different providers (Groq for LLM, Gemini for embeddings)
  - [ ] Implement provider-specific configuration properties (e.g., `lorevault.llm.chat.provider=groq`)
  - [ ] Add provider failover logic for high availability
  - [ ] Update health checks to monitor multiple providers

### Medium Priority Bug Fixes
- [ ] **Chapter Re-upload Debug Issue** - Fix failed chapter re-submission
  - [ ] Debug why same chapter fails on re-upload after initial failure
  - [ ] Investigate ingestion job cleanup after failures
  - [ ] Check for stale locks or status records blocking re-upload
  - [ ] Add better error messaging for duplicate submission attempts
  - [ ] Ensure proper cleanup of partial ingestion state

## Architectural Principles Applied

- ✅ **Single Responsibility Principle** - Each class has one reason to change
- ✅ **Dependency Inversion Principle** - Depend on abstractions, not concretions  
- ✅ **Command Query Separation** - Separate state changes from data retrieval
- ✅ **Strategy Pattern** - For different algorithms or processing approaches
- ✅ **Factory Pattern** - For complex object creation logic
- ✅ **Context Objects** - For managing shared state across helper methods

## Testing Strategy

- [ ] Maintain 100% test coverage during refactoring
- [ ] Use incremental refactoring to avoid breaking changes
- [ ] Validate existing integration tests pass after each step
- [ ] Add focused unit tests for extracted components

## Notes

- Following the same pattern used in ChunkEmbeddingService refactor
- Each extracted service should be focused and testable
- Maintain backward compatibility during transition
- Update documentation as services are extracted
