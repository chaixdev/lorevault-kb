# Service Consolidation Epic: Reducing Complexity

**Epic ID**: REFACTOR-001  
**Status**: Planning  
**Priority**: High  
**Effort**: 3-4 weeks  
**Goal**: Reduce 15+ micro-services to 4-6 focused business services

## Problem Statement

The current architecture has **excessive service fragmentation** that makes development painful:
- **15+ services** for basic operations
- Complex dependency webs between services  
- Difficult debugging and feature development
- Over-abstracted internal boundaries
- Test complexity from excessive mocking

## Success Criteria

- ✅ **Reduce from ~15 services to 4-6 business services**
- ✅ **Maintain functional parity** - all existing endpoints work identically  
- ✅ **Preserve architectural boundaries** - keep real ports & adapters
- ✅ **Simplify testing** - reduce mock complexity, focus on business behavior
- ✅ **Improve developer experience** - easier navigation, clearer service purposes

## Current Service Inventory Analysis

### **Ingestion Service Cluster** (5 → 1 service)
```java
// Current over-segmentation  
IngestionService                  // Just orchestration
├── ChapterValidationService      // 40 lines - just validation logic
├── IngestionJobLifecycleService  // 178 lines - just CRUD operations
├── JobQueryService              // 260 lines - just query logic  
├── IngestionWorkflowService     // 261 lines - just orchestration
└── LlmCallLoggingService        // Logging utility
```

**Analysis**: These services are **always used together** and share the same data (IngestionJob, Chapter). They represent **one business capability**: "Process chapter submissions."

### **Content Processing Cluster** (7 → 1 service)
```java
// Current over-segmentation
SceneDetectionService       // 68 lines - calls AI
├── ScenePersistenceService      // 93 lines - saves results  
├── SceneCoordinateLocalizer     // Coordinate calculations
├── SceneDetectionXmlParser      // XML parsing logic
├── TextChunkingService          // Text processing
├── ChunkEmbeddingService        // Vector generation
└── TriadOrchestrationService    // Scene coordination
```

**Analysis**: All focused on **one business process**: "Convert chapter text into structured, searchable content."

### **System Health Cluster** (4 → 1 service)
```java
// Current over-segmentation
LlmHealthCheckService
├── EmbeddingHealthCheckService
├── LlmChatSlotsHealthService  
├── LlmModelInfoService
└── Various health utilities
```

**Analysis**: All part of **one business capability**: "Monitor system health."

### **Utility Over-Abstraction** (3 → inline)
```java
// Should be methods, not services
HashService          // 10 lines - just SHA-256
PromptLoaderService  // File loading utility
Various mappers      // Data transformation
```

## Refactor Strategy: Phase-by-Phase

### **Phase 1: Eliminate Utility Services** (1 week, Low Risk)
**Goal**: Remove fake service boundaries for simple utilities

**Tickets**:
- **REFACTOR-001-1**: Inline HashService into domain entities
- **REFACTOR-001-2**: Move PromptLoaderService into LLM adapters  
- **REFACTOR-001-3**: Clean up unnecessary mapper services

**Benefits**: Immediate simplification, reduced indirection

### **Phase 2: Consolidate Ingestion Services** (1 week, Medium Risk)
**Goal**: Merge ingestion service cluster into unified business service

**Tickets**:
- **REFACTOR-001-4**: Create consolidated IngestionJobService
- **REFACTOR-001-5**: Merge job lifecycle + query operations
- **REFACTOR-001-6**: Absorb validation logic into main IngestionService
- **REFACTOR-001-7**: Update tests to focus on business workflows

**Result**: `IngestionService` (complete chapter submission workflow)

### **Phase 3: Consolidate Content Processing** (2 weeks, High Risk)  
**Goal**: Unify scene detection, chunking, and embedding into coherent service

**Tickets**:
- **REFACTOR-001-8**: Create ContentProcessingService  
- **REFACTOR-001-9**: Merge scene detection + persistence operations
- **REFACTOR-001-10**: Integrate chunking and embedding workflows
- **REFACTOR-001-11**: Consolidate coordinate localization logic
- **REFACTOR-001-12**: Update integration tests for complete workflows

**Result**: `ContentProcessingService` (complete text-to-structured-content pipeline)

### **Phase 4: Consolidate System Services** (3 days, Low Risk)
**Goal**: Unify health checking and system monitoring

**Tickets**:
- **REFACTOR-001-13**: Merge all health check services
- **REFACTOR-001-14**: Create unified SystemHealthService

**Result**: `SystemHealthService` (complete system monitoring)

### **Phase 5: Final Cleanup & Testing** (2 days, Low Risk)
**Goal**: Polish and validation

**Tickets**:
- **REFACTOR-001-15**: Remove unused interfaces and abstractions
- **REFACTOR-001-16**: Update architecture tests  
- **REFACTOR-001-17**: Comprehensive integration testing
- **REFACTOR-001-18**: Documentation updates

## Target Architecture (After Consolidation)

### **Final Service Structure**
```java
// 4 focused business services (down from 15+)
@Service IngestionService           // "Submit and process chapters"
@Service ContentProcessingService   // "Convert text to structured content"  
@Service SearchService             // "Find and retrieve content" (existing)
@Service SystemHealthService       // "Monitor system status"
```

### **Preserved Architectural Boundaries**
```java
// Keep real ports (external system boundaries)
ContentPersistencePort    // Database operations
SceneDetectionPort       // LLM API calls  
EmbeddingPort           // Vector service calls
SearchPort              // Search operations
```

### **Service Responsibilities**

**`IngestionService`** (~200-300 lines):
- Chapter submission validation  
- Job lifecycle management (create, update, complete)
- Job status queries and listing
- Workflow orchestration
- Event publishing

**`ContentProcessingService`** (~300-400 lines):
- Scene detection (LLM integration)
- Text chunking and segmentation
- Vector embedding generation  
- Content persistence coordination
- Cross-chapter context handling

**`SearchService`** (existing, ~150 lines):
- Semantic search operations
- Content retrieval and filtering
- Result formatting

**`SystemHealthService`** (~150-200 lines):  
- LLM health monitoring
- Embedding service health checks
- System status aggregation
- Health metrics collection

## Testing Strategy Simplification

### **Before: Complex Mock Webs**
```java
@Mock ChapterValidationService mockValidation;
@Mock IngestionJobLifecycleService mockLifecycle;  
@Mock JobQueryService mockQuery;
@Mock IngestionWorkflowService mockWorkflow;
@Mock SceneDetectionService mockSceneDetection;
@Mock ScenePersistenceService mockScenePersistence; 
// 10+ mocks for one business operation!
```

### **After: Focus on Business Boundaries**
```java
@Mock ContentPersistencePort mockPersistence;  // Real boundary
@Mock SceneDetectionPort mockSceneDetection;   // Real boundary
@Mock EmbeddingPort mockEmbedding;            // Real boundary

// Test complete business workflows, not internal service choreography
```

### **Test Consolidation Strategy**
- **Service Tests**: Test complete business workflows with mocked ports
- **Integration Tests**: Focus on critical external integrations
- **Remove**: Internal service interaction tests (artificial boundaries)

## Risk Mitigation

### **High-Risk Areas**
1. **Content Processing**: Complex AI integration logic
2. **Scene Detection**: Triad-based processing with cross-chapter dependencies
3. **Job Status**: Complex status tracking with multiple states

### **Mitigation Strategies**
1. **Comprehensive Test Coverage**: Maintain 85%+ coverage during refactor
2. **Feature Flag Protection**: Keep old services until new ones validated
3. **Incremental Migration**: One service cluster at a time
4. **Integration Testing**: Focus on end-to-end workflow validation

### **Rollback Plan**
- Each phase builds on previous, allows incremental rollback
- Feature flags enable quick reversion
- Comprehensive test suite provides confidence

## Success Metrics

### **Quantitative Goals**
- **Service Count**: 15+ → 4-6 services
- **Test Count**: Maintain functionality, reduce mock complexity
- **Line Count**: Reduce overall codebase size by 10-15%
- **Dependency Complexity**: Reduce service-to-service dependencies by 80%

### **Qualitative Goals**
- **Developer Experience**: Easier navigation, clearer service purposes
- **Debugging**: Simpler call stacks, fewer indirection layers
- **Feature Development**: Faster iteration, clearer service boundaries
- **Test Maintainability**: Fewer fragile tests, focus on business behavior

## Documentation Updates

### **Architecture Documentation**
- Update service design principles (already done)
- Revise functional viewpoint with new service boundaries
- Update integration patterns and testing strategies

### **Developer Guidelines**
- Service consolidation patterns
- Testing approach for consolidated services
- Migration patterns for future service design

## Implementation Schedule

| Phase | Duration | Risk | Deliverable |
|-------|----------|------|-------------|
| Phase 1 | 1 week | Low | Utility services eliminated |
| Phase 2 | 1 week | Med | Ingestion services consolidated |  
| Phase 3 | 2 weeks | High | Content processing unified |
| Phase 4 | 3 days | Low | System services merged |
| Phase 5 | 2 days | Low | Testing and cleanup complete |

**Total Duration**: 3-4 weeks  
**Resource**: 1 developer (with LLM assistance)  
**Dependencies**: None (internal refactor)

## Next Steps

1. ✅ **Epic Planning Complete** - This document
2. 🔲 **Phase 1 Ticket Creation** - Individual implementation tickets
3. 🔲 **Development Setup** - Feature flags, testing strategy
4. 🔲 **Phase 1 Implementation** - Start with utility elimination
5. 🔲 **Iterative Validation** - Test after each phase

This epic transforms LoreVault from a **micro-service complexity nightmare** into a **maintainable, focused business service architecture** while preserving all functionality and proper architectural boundaries.
