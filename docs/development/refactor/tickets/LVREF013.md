# LVREF013: Merge Health Check Services



**Priority**: Medium  

**Effort**: 4 hours  

**Risk**: Low  

**Phase**: 4 (Consolidate System Services)  

**Dependencies**: None (can run parallel to Phase 3)



## Problem Statement



Health checking is split across `LlmHealthCheckService`, `EmbeddingHealthCheckService`, `LlmChatSlotsHealthService`, and `LlmModelInfoService`. All serve the same business purpose: "Monitor system health."



## Current State



```java

// Multiple health check services

LlmHealthCheckService

├── EmbeddingHealthCheckService

├── LlmChatSlotsHealthService  

├── LlmModelInfoService

└── Various health utilities

```



## Target State



```java

@Service

public class SystemHealthService {

    private final LlmPort llmPort;

    private final EmbeddingPort embeddingPort;

    private final HealthCache healthCache; // TTL cache for expensive checks

    

    // Individual health checks with caching and timeouts

    @Cacheable(value = "health-llm", unless = "#result.status == 'DOWN'")

    public HealthStatus checkLlmHealth() { 

        return timeoutWrapper(() -> llmPort.ping(), Duration.ofSeconds(5));

    }

    

    // Aggregated health with fallback

    public SystemHealthResponse getOverallSystemHealth() {

        // Parallel execution, fallback to "UNKNOWN" on timeout/error

    }

}

```



**Enhancements**:

- **Caching**: Cache UP results briefly (30s-2m TTL) to avoid rate limiting

- **Timeouts**: 5s default timeout per check; configurable via properties

- **Fallbacks**: Graceful degradation when external systems are slow

- **Actuator Integration**: Expose via Spring Boot Actuator `/health` endpoint

- **Standard Schema**: Consistent `{status, timestamp, details, version}` response format



## Implementation Steps



1. Create new `SystemHealthService` class

2. Move all health check methods from existing services

3. Consolidate health aggregation logic

4. Update health endpoints to use unified service

5. Remove old health check service classes



## Acceptance Criteria



- [ ] Single `SystemHealthService` handles all health checks

- [ ] All health endpoints work identically

- [ ] Health check aggregation logic preserved

- [ ] Health metrics collection maintained

- [ ] Individual health check logic preserved



## Files to Modify



**Files to CREATE**:

- `SystemHealthService.java` - Unified health service

- `SystemHealthServiceTest.java` - Consolidated tests



**Files to DELETE**:

- `LlmHealthCheckService.java`

- `EmbeddingHealthCheckService.java`

- `LlmChatSlotsHealthService.java`

- `LlmModelInfoService.java`

- Related test files



**Files to UPDATE**:

- Health endpoint controllers - Update dependencies

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



