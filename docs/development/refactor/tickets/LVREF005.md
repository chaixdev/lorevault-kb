# LVREF005: Absorb Validation Logic into Main Service

**Priority**: High  
**Effort**: 4 hours  
**Risk**: Low  
**Phase**: 2 (Consolidate Ingestion Services)  
**Dependencies**: LVREF004

## Problem Statement

`ChapterValidationService` is 40 lines of validation logic that's only used by IngestionService`. Creating a service for this adds unnecessary indirection.

## Current State

```java
@Service  
public class ChapterValidationService {
    public ChapterValidationResult validateAndProcessChapter(request) {
        // Just validation and deduplication logic
    }
}

// Only caller
IngestionService.submitChapter() -> chapterValidationService.validateAndProcessChapter)
```

## Target State

```java
@Service
public class IngestionService {
    
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
        ChapterValidationResult result = validateAndProcessChapter(request);
        // Continue with rest of workflow
    }
    
    private ChapterValidationResult validateAndProcessChapter(SubmitChapterRequest equest) {
        // Same validation logic, now as private method
    }
}
```

## Implementation Steps

1. Prefer Bean Validation (Jakarta Validation) on request DTOs where possible `@NotBlank`, `@Size`, custom constraints)
2. For cross-field or domain-specific rules, move logic into a domain-level validator tility (not a Spring service)
3. Keep a thin private method in `IngestionService` to orchestrate validation calls
4. Move `ChapterValidationResult` as inner class or separate record if needed
5. Remove `ChapterValidationService` class and Spring bean
6. Update tests to focus on business workflow, not validation details

## Acceptance Criteria

- [ ] No `ChapterValidationService` class exists
- [ ] All validation logic preserved as private method
- [ ] `ChapterValidationResult` preserved as inner class or record
- [ ] All existing validation behavior maintained
- [ ] Tests focus on business workflow, not validation details

## Files to Modify

**Files to DELETE**:

- `ChapterValidationService.java`
- `ChapterValidationServiceTest.java`


- `IngestionService.java` - Add private validation method
- `IngestionServiceTest.java` - Test validation as part of workflow

## Testing Strategy

- Validate DTO annotations with a validator in unit tests
- Test domain validator utility with targeted unit tests
- Test validation as part of complete chapter submission workflow
- Focus on business behavior rather than validation service interactions

## Risk Assessment

**Low Risk** - Simple method move with no business logic changes.
