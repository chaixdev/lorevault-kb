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

## Phase 3: Consolidate Content Processing (1.5 weeks, Medium Risk)

### LVREF008: Create SceneProcessingService

**Priority**: High | **Effort**: 1 day | **Risk**: Medium  
**Dependencies**: Phase 2 complete

**Problem**: Scene operations are split across 4 tightly-coupled services (`SceneDetectionService`, `ScenePersistenceService`, `SceneCoordinateLocalizer`, `SceneDetectionXmlParser`) that always work together.

**Goal**: Create unified `SceneProcessingService` for complete scene lifecycle management.

**Acceptance Criteria**:

- ✅ New `SceneProcessingService` handles scene detection, persistence, and coordination
- ✅ XML parsing logic integrated as private methods
- ✅ Scene coordinate localization included
- ✅ All scene-related endpoints work identically
- ✅ Transaction boundaries properly managed

**Files to Create**:

- `SceneProcessingService.java` - Unified scene service (~250 lines)
- `SceneProcessingServiceTest.java` - Comprehensive scene tests

**Files to Delete**:

- `SceneDetectionService.java`
- `ScenePersistenceService.java`
- `SceneCoordinateLocalizer.java`
- `SceneDetectionXmlParser.java`
- Related test files

**Files to Update**:

- Controller classes - Update to use SceneProcessingService
- Integration tests - Validate complete scene workflows

---

### LVREF009: Rename ChunkEmbeddingService to EmbeddingService

**Priority**: Medium | **Effort**: 4 hours | **Risk**: Low  
**Dependencies**: LVREF008

**Problem**: `ChunkEmbeddingService` handles more than just chunk embeddings - it's the core semantic processing service.

**Goal**: Rename and expand scope to handle all embedding and semantic analysis operations.

**Acceptance Criteria**:

- ✅ Service renamed to `EmbeddingService`
- ✅ All functionality preserved with identical behavior
- ✅ Class references updated throughout codebase
- ✅ Tests renamed and updated
- ✅ Documentation updated

**Files to Rename**:

- `ChunkEmbeddingService.java` → `EmbeddingService.java`
- `ChunkEmbeddingServiceTest.java` → `EmbeddingServiceTest.java`

**Files to Update**:

- All classes that inject ChunkEmbeddingService
- Spring configuration files
- Integration tests

---

### LVREF010: Integrate TriadOrchestrationService into EmbeddingService

**Priority**: Medium | **Effort**: 6 hours | **Risk**: Medium  
**Dependencies**: LVREF009

**Problem**: `TriadOrchestrationService` handles semantic analysis that's conceptually part of embedding/semantic processing.

**Goal**: Move triad orchestration logic into `EmbeddingService` as semantic analysis capability.

**Acceptance Criteria**:

- ✅ Triad orchestration methods moved to EmbeddingService
- ✅ All temporal relationship analysis preserved
- ✅ Cross-scene coordination logic maintained
- ✅ Semantic analysis endpoints work identically
- ✅ Service dependencies properly updated

**Files to Delete**:

- `TriadOrchestrationService.java`
- `TriadOrchestrationServiceTest.java`

**Files to Update**:

- `EmbeddingService.java` - Add triad orchestration methods
- Classes that use TriadOrchestrationService
- Integration tests

---

### LVREF011: Keep TextChunkingService Independent

**Priority**: Low | **Effort**: 2 hours | **Risk**: Low  
**Dependencies**: None (can run parallel)

**Problem**: `TextChunkingService` is well-designed and focused - no consolidation needed.

**Goal**: Validate that TextChunkingService remains independent and well-bounded.

**Acceptance Criteria**:

- ✅ TextChunkingService keeps current responsibilities
- ✅ Service boundaries validated and documented
- ✅ No unnecessary coupling with other services
- ✅ Configuration parameters remain clear
- ✅ Tests remain focused on chunking logic

**Files to Review**:

- `TextChunkingService.java` - Validate design and boundaries
- `TextChunkingServiceTest.java` - Ensure focused testing

---

### LVREF012: Content Processing Integration Tests

**Priority**: Medium | **Effort**: 1 day | **Risk**: Low  
**Dependencies**: LVREF008, LVREF010

**Problem**: Need integration tests for the refined 3-service content processing architecture.

**Goal**: Create comprehensive integration tests for scene processing, chunking, and embedding workflows.

**Acceptance Criteria**:

- ✅ Scene detection → persistence → coordination pipeline tested
- ✅ Text chunking workflows validated independently
- ✅ Embedding generation → semantic analysis pipeline tested
- ✅ Cross-service integration scenarios covered
- ✅ Error handling and edge cases validated

**Files to Create**:

- `ContentProcessingIntegrationTest.java` - End-to-end workflow tests

**Files to Update**:

- Individual service tests - Ensure focused unit testing
- System integration tests - Validate complete pipelines

---

## Phase 4: Consolidate System Services (3 days, Low Risk)

### LVREF013: Create SystemHealthService

**Priority**: Medium | **Effort**: 6 hours | **Risk**: Low  
**Dependencies**: None (can run parallel to Phase 3)

**Problem**: Health checking is split across 3 services (`LlmHealthCheckService`, `EmbeddingHealthCheckService`, `LlmChatSlotsHealthService`) with overlapping concerns.

**Goal**: Merge health check services into unified `SystemHealthService` while keeping `LlmModelInfoService` separate.

**Acceptance Criteria**:

- ✅ New `SystemHealthService` handles LLM and embedding health checks
- ✅ All health endpoints work identically
- ✅ Health check aggregation and retry logic preserved
- ✅ `LlmModelInfoService` remains independent (pure configuration)
- ✅ Health metrics collection maintained

**Files to Create**:

- `SystemHealthService.java` - Unified health service (~300 lines)

**Files to Delete**:

- `LlmHealthCheckService.java`
- `EmbeddingHealthCheckService.java` 
- `LlmChatSlotsHealthService.java`
- Related test files

**Files to Keep**:

- `LlmModelInfoService.java` - Model configuration metadata (stays independent)

**Files to Update**:

- Health endpoint controllers - Update to use SystemHealthService
- Health check configurations

---

### LVREF014: System Health Integration Tests

**Priority**: Low | **Effort**: 3 hours | **Risk**: Low  
**Dependencies**: LVREF013

**Problem**: Need comprehensive tests for unified health service and integration with model info service.

**Goal**: Create integration tests for complete system health monitoring.

**Acceptance Criteria**:

- ✅ All health check endpoints tested
- ✅ Health aggregation logic validated
- ✅ Integration with LlmModelInfoService tested
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
| **Phase 3** | LVREF008-012 | 1.5 weeks | Medium | Phase 2 complete |
| **Phase 4** | LVREF013-014 | 3 days | Low | Can parallel Phase 3 |
| **Phase 5** | LVREF015-018 | 2 days | Low | All phases complete |

**Total**: 18 tickets, 3.5-4 weeks, 1 developer + LLM assistance

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