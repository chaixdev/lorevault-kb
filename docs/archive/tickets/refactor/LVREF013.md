# LVREF013: Create SystemHealthService

**Priority**: Medium  
**Effort**: 6 hours  
**Risk**: Low  
**Phase**: 4 (Consolidate System Services)  
**Dependencies**: None (can run parallel to Phase 3)

## Problem Statement

Health checking is split across 3 services (`LlmHealthCheckService`, EmbeddingHealthCheckService`, `LlmChatSlotsHealthService`) with overlapping concerns, while LlmModelInfoService` serves a different purpose (configuration metadata).

## Current State

```java
// 3 health check services + 1 config service
LlmHealthCheckService          // Complex health checking with retry logic
├── EmbeddingHealthCheckService    // Simple embedding validation
├── LlmChatSlotsHealthService      // Chat client validation
└── LlmModelInfoService           // Model configuration (different concern)
```

## Target State

```java
@Service
public class SystemHealthService {
    private final LlmPort llmPort;
    private final EmbeddingPort embeddingPort;
    private final LlmModelInfoService modelInfoService; // Inject, don't absorb
    
    // Unified health checking for all AI services
    public HealthStatus checkLlmHealth() { ... }
    public HealthStatus checkEmbeddingHealth() { ... }
    public HealthStatus checkChatSlotsHealth() { ... }
    
    // Aggregated health with model info context
    public SystemHealthResponse getOverallSystemHealth() {
        // Use modelInfoService for context, but don't absorb it
    }
}

// Keep this separate - it's configuration, not health checking
@Service  
public class LlmModelInfoService {
    // Pure configuration/metadata service
    // Different lifecycle and responsibilities
}
```

## Implementation Steps

1. Create new `SystemHealthService` class
2. Move LLM health check logic from `LlmHealthCheckService`  
3. Move embedding health check logic from `EmbeddingHealthCheckService`
4. Move chat slots health check logic from `LlmChatSlotsHealthService`
5. **Keep `LlmModelInfoService` separate** - inject as dependency
6. Update health endpoints to use unified service
7. Remove old health check services (but keep model info service)

## Acceptance Criteria

- [ ] New `SystemHealthService` handles LLM and embedding health checks
- [ ] All health endpoints work identically
- [ ] Health check aggregation and retry logic preserved  
- [ ] `LlmModelInfoService` remains independent (pure configuration)
- [ ] Health metrics collection maintained
- [ ] All 3 health check services deleted, model info service preserved

## Files to Modify

**Files to CREATE**:

- `SystemHealthService.java` - Unified health service (~300 lines)

**Files to DELETE**:

- `LlmHealthCheckService.java`
- `EmbeddingHealthCheckService.java`
- `LlmChatSlotsHealthService.java`
- Related test files

**Files to KEEP**:

- `LlmModelInfoService.java` - Model configuration metadata (stays independent)

**Files to UPDATE**:

- Health endpoint controllers - Update to use SystemHealthService
- Health check configurations

## Testing Strategy

- Test all individual health check methods
- Test health aggregation logic
- Test error scenarios and timeout handling
- Validate health metrics collection

## Risk Assessment

**Low Risk** - Simple service consolidation with no complex interactions.

**Benefits**:

- Single place for all system health monitoring
- Easier to add new health checks
- Simplified health endpoint management
