# Readability Refactor Task List

## 🔄 In Progress
_Currently empty - ready to take on next task_

## 🚫 Blocked
_Currently empty_

## 🏁 Done
- [x] **[refactor]** ChunkEmbeddingService - Refactored 300+ line monolithic method into 15+ focused helpers with EmbeddingContext
- [x] **[refactor]** IngestionService - Successfully decomposed into 4 focused services (IngestionJobLifecycleService, ChapterValidationService, IngestionWorkflowService, JobQueryService)
- [x] **[refactor]** ContentIngestionController - Reduced from ~240 lines to ~95 lines (60% reduction), extracted 4 specialized services
- [x] **[refactor]** LlmHealthCheckService - Decomposed into 3 specialized services with context objects, all 39 tests passing
- [x] **[infra]** Better LLM Retry Handling - Enhanced resilience with exponential backoff + jitter, job status communication, transaction rollback fix
- [x] **[infra]** Configuration Modernization - Migrated to YAML with type-safe @ConfigurationProperties validation, organized lorevault.* namespace

## � In Progress
_Currently empty - ready to take on next task_

## 🚫 Blocked
_Currently empty_

## 📋 Backlog

### High Priority
- [ ] **[infra]** Model Parameter Configuration - Fine-tune LLM behavior via properties
  - Add `temperature` configuration for creativity control
  - Add `top-P` (nucleus sampling) parameter configuration
  - Create model-specific parameter profiles in application.yml
  - Allow runtime parameter adjustment without restart

- [ ] **[infra]** Multi-Provider LLM Configuration - Support different models for different tasks
  - Replace Spring AI auto-configuration with custom `@Configuration` classes
  - Create separate `ChatClient` beans for different providers (Groq for LLM, Gemini for embeddings)
  - Implement provider-specific configuration properties (e.g., `lorevault.llm.chat.provider=groq`)
  - Add provider failover logic for high availability
  - Update health checks to monitor multiple providers

### Medium Priority
- [ ] **[refactor]** SceneDetectionXmlParser - Stage-based processing
  - Extract `XmlResponseCleaner` (text cleanup and normalization)
  - Extract `SceneXmlValidator` (structure validation)
  - Extract `SceneDataExtractor` (DOM to domain object mapping)

- [ ] **[refactor]** TextChunkingService - Strategy pattern
  - Create `ChunkingStrategy` interface
  - Extract `SentenceAwareSlidingWindowStrategy`
  - Extract `ChunkBoundaryOptimizer`

- [ ] **[bugfix]** Chapter Re-upload Debug Issue - Fix failed chapter re-submission
  - Debug why same chapter fails on re-upload after initial failure
  - Investigate ingestion job cleanup after failures
  - Check for stale locks or status records blocking re-upload
  - Add better error messaging for duplicate submission attempts
  - Ensure proper cleanup of partial ingestion state

---

## Architectural Principles Applied
- ✅ **Single Responsibility Principle** - Each class has one reason to change
- ✅ **Dependency Inversion Principle** - Depend on abstractions, not concretions  
- ✅ **Command Query Separation** - Separate state changes from data retrieval
- ✅ **Strategy Pattern** - For different algorithms or processing approaches
- ✅ **Factory Pattern** - For complex object creation logic
- ✅ **Context Objects** - For managing shared state across helper methods

## Testing Strategy
- Maintain 100% test coverage during refactoring
- Use incremental refactoring to avoid breaking changes
- Validate existing integration tests pass after each step
- Add focused unit tests for extracted components

## Tag Reference
- **[refactor]** - Code restructuring for better maintainability
- **[infra]** - Infrastructure improvements and system resilience
- **[chore]** - Configuration, tooling, and maintenance tasks
- **[bugfix]** - Bug fixes and issue resolution
- **[feature]** - New functionality implementation
