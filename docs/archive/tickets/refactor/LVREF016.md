# LVREF016: Update Architecture Tests

**Priority**: Medium  
**Effort**: 2 hours  
**Risk**: Low  
**Phase**: 5 (Final Cleanup & Testing)  
**Dependencies**: LVREF015

## Problem Statement

Architecture tests may reference old service boundaries, class names, and package structures that have been changed during the service consolidation refactoring.

## Current State

Architecture tests may be failing or outdated due to:

- Service class names that have been changed or deleted
- Package structures that have been reorganized  
- Service dependency rules that no longer match reality
- Port/adapter boundaries that have been updated
- Service count expectations that are now incorrect

## Target State

Updated architecture tests that:

- Validate the new consolidated service structure
- Enforce correct port/adapter boundaries  
- Validate service dependency rules for new architecture
- Check package structure matches new organization
- Ensure architectural principles are maintained

## Implementation Steps

1. **Review existing architecture tests**
   - Identify tests that reference old service names
   - Find tests that check service counts or boundaries
   - Look for package structure validations
2. **Update service boundary tests**
   - Update tests to reflect new service consolidation
   - Validate SceneProcessingService boundaries
   - Check EmbeddingService responsibilities
   - Ensure TextChunkingService remains independent
3. **Update port/adapter tests**
   - Validate port interfaces are correctly used
   - Check adapter implementations follow patterns
   - Ensure no business logic in adapters
4. **Update dependency rules**
   - Validate service-to-service dependencies
   - Check that services only depend on ports
   - Ensure no circular dependencies
5. **Update package structure tests**
   - Validate package organization
   - Check import restrictions
   - Ensure layer separation
6. **Update service count expectations**
   - Adjust tests that count total services
   - Update expectations for service categories

## Acceptance Criteria

- [ ] Architecture tests pass with new service structure
- [ ] Port/adapter boundary tests updated
- [ ] Service dependency rules updated
- [ ] Package structure tests updated
- [ ] No false positives from old service references
- [ ] New service boundaries properly validated

## Files to Modify

**Files to UPDATE**:

- `PortsAndAdaptersArchitectureTest.java` - Update for new service structure
- Package structure validation tests - Update package rules
- Service dependency validation tests - Update dependency expectations
- Service count tests - Update expected counts
- Import restriction tests - Update for new packages

## Testing Strategy

**Architecture Test Categories:**

1. **Service Structure Tests**
   - Validate service consolidation boundaries
   - Check service naming conventions
   - Ensure services follow patterns

2. **Port/Adapter Tests**
   - Validate port interface usage
   - Check adapter implementation patterns
   - Ensure proper separation of concerns

3. **Dependency Tests**
   - Check service-to-service dependencies
   - Validate port usage patterns
   - Ensure no circular dependencies

4. **Package Structure Tests**
   - Validate package organization
   - Check layer separation
   - Ensure import restrictions

5. **Naming Convention Tests**
   - Validate service naming patterns
   - Check class naming conventions
   - Ensure consistent patterns

## Risk Assessment

**Low Risk** - Test updates with no functional changes.

**Benefits**:

- Maintain architectural discipline
- Document new service boundaries
- Prevent architectural drift
- Validate refactoring success
- Ensure consistent patterns

**Validation**:

- All architecture tests pass
- No false positives from old references
- New architectural rules are enforced
