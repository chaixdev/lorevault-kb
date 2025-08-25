# LoreVault Refactor Tickets (LVREF###)

**Epic**: Service Consolidation & Complexity Reduction  
**Total Estimated Effort**: 3-4 weeks  
**Success Criteria**: Reduce 15+ micro-services to 4-6 focused business services

---

## Phase 1: Eliminate Utility Services (1 week, Low Risk)

### LVREF001: Inline HashService into Domain Entities
**Priority**: High | **Effort**: 2 hours | **Risk**: Low  
**Dependencies**: None

**Problem**: `HashService` is a 10-line utility that just wraps SHA-256 generation. Creating a service for this adds unnecessary indirection and Spring injection complexity.

**Goal**: Move hash generation logic directly into domain entities as static utility methods.

**Acceptance Criteria**:
- ✅ No `HashService` class exists
- ✅ All hash generation uses static utility method  
- ✅ All tests pass with identical behavior
- ✅ No Spring injection of hash functionality

**Files to Modify**:
- `HashService.java` - DELETE
- `Chapter.java` - Add static hash method
- `IngestionWorkflowService.java` - Update hash calls
- `ChapterValidationService.java` - Update hash calls
- Related test files

---

### LVREF002: Move PromptLoaderService into LLM Adapters
**Priority**: Medium | **Effort**: 4 hours | **Risk**: Low  
**Dependencies**: None

**Problem**: `PromptLoaderService` is just loading text files from resources. This is infrastructure concern that belongs in the LLM adapters, not as a separate business service.

**Goal**: Make prompt loading part of LLM adapter initialization, removing the service abstraction.

**Acceptance Criteria**:
- ✅ No `PromptLoaderService` class exists
- ✅ LLM adapters manage their own prompt loading
- ✅ All LLM functionality works identically  
- ✅ Configuration remains clean and testable

**Files to Modify**:
- `PromptLoaderService.java` - DELETE
- `OpenAiSceneDetector.java` - Add prompt loading
- LLM adapter classes - Self-manage prompts
- Configuration classes - Simplify prompt config

---

### LVREF003: Clean Up Unnecessary Mapper Services
**Priority**: Low | **Effort**: 2 hours | **Risk**: Low  
**Dependencies**: None

**Problem**: Various small mapper services exist for simple data transformations that could be static methods or inline conversions.

**Goal**: Convert mapper services to static utility methods or inline logic.

**Acceptance Criteria**:
- ✅ No services exist that just do data transformation
- ✅ All mapping logic preserved with identical behavior
- ✅ Tests simplified by removing unnecessary mocks
- ✅ Code is more direct and easier to follow

**Files to Modify**: TBD after service audit

---

## Phase 2: Consolidate Ingestion Services (1 week, Medium Risk)

### LVREF004: Create Consolidated IngestionJobService
**Priority**: High | **Effort**: 1 day | **Risk**: Medium  
**Dependencies**: LVREF001, LVREF002, LVREF003

**Problem**: Job management is artificially split across `IngestionJobLifecycleService` (178 lines) and `JobQueryService` (260 lines) that always work together.

**Goal**: Merge job lifecycle and query operations into single focused service.

**Acceptance Criteria**:
- ✅ Single `IngestionJobService` handles all job operations
- ✅ All job lifecycle methods (create, update, complete, fail) preserved
- ✅ All query methods (getStatus, listJobs) preserved  
- ✅ Consolidated tests focus on complete job workflows
- ✅ No behavioral changes to existing endpoints

**Files to Create**:
- `IngestionJobService.java` - New consolidated service

**Files to Delete**:
- `IngestionJobLifecycleService.java`
- `JobQueryService.java`
- Related test files

**Files to Update**:
- `IngestionService.java` - Use single job service dependency
- Controller classes - Update dependency injection

---

### LVREF005: Absorb Validation Logic into Main Service
**Priority**: High | **Effort**: 4 hours | **Risk**: Low  
**Dependencies**: LVREF004

**Problem**: `ChapterValidationService` is 40 lines of validation logic that's only used by `IngestionService`. Creating a service for this adds unnecessary indirection.

**Goal**: Move validation logic as private method in `IngestionService`.

**Acceptance Criteria**:
- ✅ No `ChapterValidationService` class exists
- ✅ All validation logic preserved as private method
- ✅ `ChapterValidationResult` preserved as inner class or record
- ✅ All existing validation behavior maintained
- ✅ Tests focus on business workflow, not validation details

**Files to Delete**:
- `ChapterValidationService.java`
- `ChapterValidationServiceTest.java`

**Files to Update**:
- `IngestionService.java` - Add private validation method
- `IngestionServiceTest.java` - Test validation as part of workflow

---

### LVREF006: Merge Workflow Orchestration
**Priority**: High | **Effort**: 6 hours | **Risk**: Medium  
**Dependencies**: LVREF005

**Problem**: `IngestionWorkflowService` (261 lines) just orchestrates calls to other services. The workflow IS the business service.

**Goal**: Move workflow orchestration directly into `IngestionService`, removing intermediate abstraction layer.

**Acceptance Criteria**:
- ✅ No `IngestionWorkflowService` class exists
- ✅ `IngestionService` directly orchestrates complete workflow
- ✅ Dependencies changed from services to real ports
- ✅ All processing steps preserved (scene detection, chunking, embeddings)
- ✅ Event publishing behavior maintained

**Files to Delete**:
- `IngestionWorkflowService.java`
- `IngestionWorkflowServiceTest.java`

**Files to Update**:
- `IngestionService.java` - Add workflow orchestration
- Integration test files - Test complete workflow

---

### LVREF007: Consolidate Ingestion Service Tests
**Priority**: Medium | **Effort**: 1 day | **Risk**: Low  
**Dependencies**: LVREF006

**Problem**: Tests are spread across 5+ service test classes, mostly testing artificial service boundaries rather than business behavior.

**Goal**: Consolidate all ingestion-related tests into `IngestionServiceTest`, focusing on business workflows.

**Acceptance Criteria**:
- ✅ Single `IngestionServiceTest` covers all ingestion functionality
- ✅ Tests focus on business behavior, not service interactions
- ✅ Only mock real external boundaries (ports)
- ✅ Comprehensive integration tests for end-to-end workflows
- ✅ All old service test classes removed

**Files to Delete**:
- Multiple service test classes related to ingestion
- Tests that just verify "serviceA calls serviceB"

**Files to Update**:
- `IngestionServiceTest.java` - Comprehensive business workflow tests
- Integration test files - End-to-end validation

---

## Phase 3: Consolidate Content Processing (2 weeks, High Risk)

### LVREF008: Create ContentProcessingService Foundation
**Priority**: High | **Effort**: 1 day | **Risk**: Medium  
**Dependencies**: Phase 2 complete

**Problem**: Content processing is split across 7 services (`SceneDetectionService`, `ScenePersistenceService`, `SceneCoordinateLocalizer`, `TextChunkingService`, `ChunkEmbeddingService`, `TriadOrchestrationService`, `SceneDetectionXmlParser`) for one business process.

**Goal**: Create new `ContentProcessingService` as foundation for content processing consolidation.

**Acceptance Criteria**:
- ✅ New `ContentProcessingService` class created
- ✅ Basic service structure with port dependencies
- ✅ Service registered in Spring configuration
- ✅ Initial tests created
- ✅ No functional changes yet

**Files to Create**:
- `ContentProcessingService.java` - New service foundation
- `ContentProcessingServiceTest.java` - Initial test structure

---

### LVREF009: Merge Scene Detection and Persistence
**Priority**: High | **Effort**: 2 days | **Risk**: High  
**Dependencies**: LVREF008

**Problem**: `SceneDetectionService` (68 lines) and `ScenePersistenceService` (93 lines) are always used together and share scene data structures.

**Goal**: Merge scene detection and persistence operations into `ContentProcessingService`.

**Acceptance Criteria**:
- ✅ Scene detection logic moved to `ContentProcessingService`
- ✅ Scene persistence logic merged into same service
- ✅ All scene detection endpoints work identically
- ✅ Scene persistence behavior preserved
- ✅ XML parsing logic integrated

**Files to Delete**:
- `SceneDetectionService.java`
- `ScenePersistenceService.java`
- `SceneDetectionXmlParser.java`
- Related test files

**Files to Update**:
- `ContentProcessingService.java` - Add scene operations
- Controller classes - Update dependencies
- Integration tests - Validate scene workflows

---

### LVREF010: Integrate Chunking and Embedding Workflows
**Priority**: High | **Effort**: 2 days | **Risk**: High  
**Dependencies**: LVREF009

**Problem**: `TextChunkingService` and `ChunkEmbeddingService` are sequential operations that are always used together.

**Goal**: Integrate text chunking and embedding generation into unified content processing workflow.

**Acceptance Criteria**:
- ✅ Text chunking logic moved to `ContentProcessingService`
- ✅ Chunk embedding generation integrated
- ✅ Sequential processing workflow preserved
- ✅ All embedding endpoints work identically
- ✅ Chunk size and overlap parameters maintained

**Files to Delete**:
- `TextChunkingService.java`
- `ChunkEmbeddingService.java`
- Related test files

**Files to Update**:
- `ContentProcessingService.java` - Add chunking and embedding
- Configuration classes - Chunking parameters
- Integration tests - Validate complete processing pipeline

---

### LVREF011: Consolidate Scene Coordination Logic
**Priority**: Medium | **Effort**: 1 day | **Risk**: Medium  
**Dependencies**: LVREF010

**Problem**: `TriadOrchestrationService` and `SceneCoordinateLocalizer` handle scene coordination and positioning that's part of the content processing workflow.

**Goal**: Move scene coordination and localization logic into `ContentProcessingService`.

**Acceptance Criteria**:
- ✅ Triad orchestration logic integrated
- ✅ Scene coordinate localization moved
- ✅ Cross-chapter scene coordination preserved
- ✅ Scene positioning calculations maintained
- ✅ All coordinate-based queries work identically

**Files to Delete**:
- `TriadOrchestrationService.java`
- `SceneCoordinateLocalizer.java`
- Related test files

**Files to Update**:
- `ContentProcessingService.java` - Add coordination logic
- Scene-related query classes - Update dependencies

---

### LVREF012: Content Processing Integration Tests
**Priority**: Medium | **Effort**: 1 day | **Risk**: Low  
**Dependencies**: LVREF011

**Problem**: Content processing tests are scattered across multiple service test classes.

**Goal**: Create comprehensive integration tests for unified content processing workflows.

**Acceptance Criteria**:
- ✅ Complete end-to-end content processing tests
- ✅ Scene detection → chunking → embedding pipeline tested
- ✅ Cross-chapter processing scenarios covered
- ✅ Error handling and edge cases validated
- ✅ Performance regression tests included

**Files to Create**:
- `ContentProcessingIntegrationTest.java` - Comprehensive workflow tests

**Files to Update**:
- `ContentProcessingServiceTest.java` - Complete unit test coverage
- Related integration test suites

---

## Phase 4: Consolidate System Services (3 days, Low Risk)

### LVREF013: Merge Health Check Services
**Priority**: Medium | **Effort**: 4 hours | **Risk**: Low  
**Dependencies**: None (can run parallel to Phase 3)

**Problem**: Health checking is split across `LlmHealthCheckService`, `EmbeddingHealthCheckService`, `LlmChatSlotsHealthService`, and `LlmModelInfoService`.

**Goal**: Merge all health check services into unified `SystemHealthService`.

**Acceptance Criteria**:
- ✅ Single `SystemHealthService` handles all health checks
- ✅ All health endpoints work identically
- ✅ Health check aggregation logic preserved
- ✅ Health metrics collection maintained
- ✅ Individual health check logic preserved

**Files to Create**:
- `SystemHealthService.java` - Unified health service

**Files to Delete**:
- `LlmHealthCheckService.java`
- `EmbeddingHealthCheckService.java`
- `LlmChatSlotsHealthService.java`
- `LlmModelInfoService.java`
- Related test files

**Files to Update**:
- Health endpoint controllers - Update dependencies
- Health check configurations

---

### LVREF014: System Health Integration Tests
**Priority**: Low | **Effort**: 2 hours | **Risk**: Low  
**Dependencies**: LVREF013

**Problem**: Need comprehensive tests for unified health service.

**Goal**: Create integration tests for complete system health monitoring.

**Acceptance Criteria**:
- ✅ All health check endpoints tested
- ✅ Health aggregation logic validated
- ✅ Error scenarios covered
- ✅ Health metrics collection tested

**Files to Create**:
- `SystemHealthIntegrationTest.java` - Complete health check tests

---

## Phase 5: Final Cleanup & Testing (2 days, Low Risk)

### LVREF015: Remove Unused Interfaces and Abstractions
**Priority**: Low | **Effort**: 4 hours | **Risk**: Low  
**Dependencies**: All previous phases

**Problem**: Consolidation may leave unused interfaces, abstract classes, or utility methods.

**Goal**: Clean up unused code artifacts from service consolidation.

**Acceptance Criteria**:
- ✅ No unused interfaces or abstract classes
- ✅ No unused utility methods or helper classes
- ✅ Import statements cleaned up
- ✅ Configuration classes simplified
- ✅ No dead code or commented-out sections

**Files to Audit**: All remaining service classes and interfaces

---

### LVREF016: Update Architecture Tests
**Priority**: Medium | **Effort**: 2 hours | **Risk**: Low  
**Dependencies**: LVREF015

**Problem**: Architecture tests may reference old service boundaries and class names.

**Goal**: Update architecture tests to reflect new service structure.

**Acceptance Criteria**:
- ✅ Architecture tests pass with new service structure
- ✅ Port/adapter boundary tests updated
- ✅ Service dependency rules updated
- ✅ Package structure tests updated

**Files to Update**:
- `PortsAndAdaptersArchitectureTest.java`
- Package structure validation tests
- Service dependency validation tests

---

### LVREF017: Comprehensive Integration Testing
**Priority**: High | **Effort**: 1 day | **Risk**: Low  
**Dependencies**: LVREF016

**Problem**: Need end-to-end validation that all consolidation preserves system functionality.

**Goal**: Run comprehensive integration tests validating complete system functionality.

**Acceptance Criteria**:
- ✅ All API endpoints work identically to pre-refactor
- ✅ Complete chapter submission → processing → search workflow tested
- ✅ Error handling behavior preserved
- ✅ Performance benchmarks show no regression
- ✅ Event publishing behavior validated

**Files to Update**:
- Integration test suites
- Performance test benchmarks
- API compatibility tests

---

### LVREF018: Documentation Updates
**Priority**: Medium | **Effort**: 4 hours | **Risk**: Low  
**Dependencies**: LVREF017

**Problem**: Documentation needs to reflect new service architecture.

**Goal**: Update all documentation to reflect consolidated service structure.

**Acceptance Criteria**:
- ✅ Architecture documentation updated with new service boundaries
- ✅ API documentation reflects new service responsibilities  
- ✅ Developer guides updated with new service structure
- ✅ Testing documentation updated for consolidated approach

**Files to Update**:
- `/docs/architecture/` - Service architecture diagrams
- `/docs/api/` - API documentation
- `/docs/development/` - Developer guides
- `README.md` files - Service descriptions

---

## Implementation Schedule

| Phase | Tickets | Duration | Risk | Dependencies |
|-------|---------|----------|------|--------------|
| **Phase 1** | LVREF001-003 | 1 week | Low | None |
| **Phase 2** | LVREF004-007 | 1 week | Medium | Phase 1 complete |
| **Phase 3** | LVREF008-012 | 2 weeks | High | Phase 2 complete |
| **Phase 4** | LVREF013-014 | 3 days | Low | Can parallel Phase 3 |
| **Phase 5** | LVREF015-018 | 2 days | Low | All phases complete |

**Total**: 18 tickets, 3-4 weeks, 1 developer + LLM assistance

## Success Metrics

**Quantitative Goals**:
- **Service Count**: 15+ → 4-6 services ✓
- **Test Complexity**: Reduce mock objects by ~70% ✓  
- **Line Count**: 10-15% reduction through consolidation ✓
- **Service Dependencies**: 80% reduction in service-to-service calls ✓

**Qualitative Goals**:
- **Developer Experience**: Easier navigation, clearer service purposes ✓
- **Debugging**: Simpler call stacks, fewer indirection layers ✓
- **Feature Development**: Faster iteration, clearer boundaries ✓
- **Test Maintainability**: Focus on business behavior, not service choreography ✓

---

*This ticket breakdown transforms a complex service consolidation epic into discrete, manageable work items with clear acceptance criteria, dependencies, and risk assessments.*