# LVREF004: Create Consolidated IngestionJobService

**Priority**: High  
**Effort**: 1 day  
**Risk**: Medium  
**Phase**: 2 (Consolidate Ingestion Services)  
**Dependencies**: LVREF001, LVREF002, LVREF003

## Problem Statement

Job management is artificially split across `IngestionJobLifecycleService` (178 lines) and `JobQueryService` (260 lines) that always work together and share the same data structures.

## Current State

```java
IngestionJobLifecycleService.createIngestionJob()
IngestionJobLifecycleService.updateJobStatus()  
IngestionJobLifecycleService.completeJob()
JobQueryService.getJobStatus()
JobQueryService.listJobs()
```

## Target State

```java
@Service
public class IngestionJobService {
    private final ContentPersistencePort persistencePort;
    
    // Lifecycle operations
    public IngestionJob createJob(UUID chapterId) { ... }
    public void updateJobStatus(UUID jobId, IngestionStatus status, String message)  ... }
    public void completeJob(UUID jobId, int chapterLength) { ... }
    public void failJob(UUID jobId, String error) { ... }
    
    // Query operations  
    public Optional<JobStatusResponse> getJobStatus(UUID jobId) { ... }
    public JobListResponse listJobs(String universe, String status, int limit, int ffset) { ... }
}
```

## Implementation Steps

1. Create new `IngestionJobService` class
2. Move all methods from `IngestionJobLifecycleService` and `JobQueryService`
3. Consolidate overlapping status record logic
4. Introduce clear `@Transactional` boundaries (avoid wrapping long-running work)
5. Update `IngestionService` to use single job service dependency
6. Add structured logging and metrics (counters for created/failed jobs)
7. Ensure pagination/sorting defaults preserved for list operations
8. Preserve event publishing behavior (if any) and topic names
9. Write consolidated service tests
10. Delete old service classes

## Acceptance Criteria

- [ ] Single `IngestionJobService` handles all job operations
- [ ] All job lifecycle methods (create, update, complete, fail) preserved
- [ ] All query methods (getStatus, listJobs) preserved
- [ ] Transactional boundaries defined; no long-running work inside
- [ ] Logging/metrics emitted for key operations
- [ ] Consolidated tests focus on complete job workflows
- [ ] No behavioral changes to existing endpoints

## Files to Modify

**Files to CREATE**:

- `IngestionJobService.java` - New consolidated service
- `IngestionJobServiceTest.java` - Consolidated tests

**Files to DELETE**:

- `IngestionJobLifecycleService.java`
- `JobQueryService.java`
- `IngestionJobLifecycleServiceTest.java`
- `JobQueryServiceTest.java`

**Files to UPDATE**:

- `IngestionService.java` - Use single job service dependency
- Controller classes - Update dependency injection

## Testing Strategy

```java
@Test
class IngestionJobServiceTest {
    @Mock ContentPersistencePort persistencePort;
    @InjectMocks IngestionJobService jobService;
    
    @Test
    void shouldCreateJobAndManageFullLifecycle() {
        // Test complete job workflow in single test
        // More valuable than testing each operation separately
    }
}
```

## Risk Assessment

**Medium Risk** - Merging two services that may have subtle interaction patterns.

**Mitigation**: 

- Comprehensive test coverage during migration
- Preserve all existing method signatures initially
- Test complete workflows, not just individual methods
- Use feature flag to switch between old/new implementation during rollout
- Add temporary dual-write or shadow-read if needed to validate parity