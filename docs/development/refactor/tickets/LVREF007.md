# LVREF007: Consolidate Ingestion Service Tests

**Priority**: Medium  
**Effort**: 1 day  
**Risk**: Low  
**Phase**: 2 (Consolidate Ingestion Services)  
**Dependencies**: LVREF006

## Problem Statement

Tests are spread across 5+ service test classes, mostly testing artificial service boundaries rather than business behavior.

## Current State

```java
// Scattered test classes
IngestionServiceTest
ChapterValidationServiceTest  
IngestionJobLifecycleServiceTest
JobQueryServiceTest
IngestionWorkflowServiceTest

// Tests focus on service interactions rather than business behavior
verify(mockValidationService).validateChapter(any());
verify(mockJobService).createJob(any());
verify(mockWorkflowService).processChapter(any());
```

## Target State

```java
@Test
class IngestionServiceTest {
    @Mock ContentPersistencePort persistencePort;
    @Mock SceneDetectionPort sceneDetectionPort;  
    // Only mock real external boundaries
    
    @Test
    @DisplayName("Should handle complete chapter submission workflow")
    void submitChapter_ShouldHandleCompleteWorkflow() {
        // Test the entire business operation
        // This is more valuable than testing internal service calls
    }
    
    @Test 
    @DisplayName("Should handle duplicate chapter gracefully")
    void submitChapter_WhenDuplicate_ShouldReturnExistingChapter() {
        // Test business rules, not validation service interactions
    }
}
```

## Implementation Steps

1. Consolidate all ingestion-related test classes into `IngestionServiceTest`
2. Rewrite tests to focus on business behavior, not service interactions
3. Remove tests that just verify "serviceA calls serviceB" - these are implementation details
4. Add comprehensive integration tests for end-to-end workflows
5. Delete old service test classes

## Acceptance Criteria

- [ ] Single `IngestionServiceTest` covers all ingestion functionality
- [ ] Tests focus on business behavior, not service interactions
- [ ] Only mock real external boundaries (ports)
- [ ] Comprehensive integration tests for end-to-end workflows
- [ ] All old service test classes removed

## Files to Modify

**Files to DELETE**:
- `ChapterValidationServiceTest.java`
- `IngestionJobLifecycleServiceTest.java`
- `JobQueryServiceTest.java`
- `IngestionWorkflowServiceTest.java`
- Tests that just verify "serviceA calls serviceB"

**Files to UPDATE**:
- `IngestionServiceTest.java` - Comprehensive business workflow tests
- Integration test files - End-to-end validation

## Testing Strategy

Focus on **business workflows** rather than service choreography:

- Test complete chapter submission → processing → completion workflow
- Test error scenarios and edge cases
- Test duplicate chapter handling
- Test job status tracking throughout workflow
- Validate event publishing at appropriate times

## Risk Assessment

**Low Risk** - Test consolidation with focus on business behavior.

**Benefits**:
- More valuable tests that actually verify business functionality
- Fewer brittle tests that break on internal refactoring
- Clearer test organization and intent

**Rollback Plan**: Can recreate individual service tests if consolidated approach proves insufficient.