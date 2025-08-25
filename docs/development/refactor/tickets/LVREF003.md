# LVREF003: Clean Up Unnecessary Mapper Services

**Priority**: Low  
**Effort**: 2 hours  
**Risk**: Low  
**Phase**: 1 (Eliminate Utility Services)  
**Dependencies**: None

## Problem Statement

Various small mapper services exist for simple data transformations that could be static methods or inline conversions.

## Current State Analysis

Identify and eliminate services that are just:

- Data transformation utilities
- Simple mapping between DTOs and domain objects
- Single-method services with no external dependencies

## Target State

```java
// Instead of mapper services, use:
// 1. Static utility methods for complex mappings
// 2. Inline conversions for simple mappings  
// 3. Builder patterns in DTOs/domain objects
```

## Implementation Steps

1. Audit existing mapper services
2. Identify services that are just utility functions
3. Convert to static utility methods or inline logic
4. Remove unnecessary service classes
5. Update tests to not mock simple transformations

## Acceptance Criteria

- [ ] No services exist that just do data transformation
- [ ] All mapping logic preserved with identical behavior
- [ ] Tests simplified by removing unnecessary mocks
- [ ] Code is more direct and easier to follow

## Files to Modify

**Files to AUDIT**:
- All `*Mapper.java` classes
- Services with single transformation methods
- DTO conversion utilities

**Actions TBD**: Specific files will be identified during service audit

## Testing Strategy

- Remove unnecessary transformation mocks
- Test actual transformation behavior, not service interactions
- Focus on data accuracy rather than service choreography

## Risk Assessment

**Low Risk** - Simple utility function consolidation.

**Rollback Plan**: Easy to recreate mapper services if inline conversions prove insufficient.