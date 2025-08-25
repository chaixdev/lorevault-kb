# LVREF001: Inline HashService into Domain Entities

**Priority**: High  
**Effort**: 2 hours  
**Risk**: Low  
**Phase**: 1 (Eliminate Utility Services)  
**Dependencies**: None

## Problem Statement

`HashService` is a 10-line utility that just wraps SHA-256 generation. Creating a service for this adds unnecessary indirection and Spring injection complexity.

## Current State

```java
@Service
public class HashService {
    public String generateSha256Hash(String text) {
        // 10 lines of SHA-256 code
    }
}

// Usage scattered across services
@Autowired HashService hashService;
String hash = hashService.generateSha256Hash(content);
```

## Target State

```java
// Create utility class in utils package
package com.lorevault.api.util;

public final class HashUtils {
    private HashUtils() {} // Utility class
    
    public static String generateSha256Hash(String text) {
        // Same 10 lines, but as static utility method
        // No Spring dependencies, just pure utility function
    }
}

// Usage in domain/service classes
import static com.lorevault.api.util.HashUtils.generateSha256Hash;

public class Chapter {
    public void updateContentHash() {
        this.contentHash = generateSha256Hash(this.rawText);
    }
}
```

## Implementation Steps

1. Create `HashUtils` class in `com.lorevault.api.util` package
2. Move hash generation logic from `HashService` to static utility method
3. Update all callers to use static import and method
4. Remove `HashService` class and Spring bean
5. Update tests to not mock hash generation (test actual hash values)

## Acceptance Criteria

- [ ] No `HashService` class exists
- [ ] All hash generation uses `HashUtils.generateSha256Hash()` static method
- [ ] All tests pass with identical behavior
- [ ] No Spring injection of hash functionality
- [ ] Hash utility properly placed in util package

## Files to Modify

**Files to CREATE**:
- `HashUtils.java` - New utility class in util package

**Files to DELETE**:
- `HashService.java`
- `HashServiceTest.java`

**Files to UPDATE**:
- `IngestionWorkflowService.java` - Update to use HashUtils
- `ChapterValidationService.java` - Update to use HashUtils
- Related test files - Remove hash service mocks, test actual hash values

## Testing Strategy

- Remove hash service mocks from tests
- Test actual hash values, not just "service was called"
- Verify identical hash generation behavior

## Risk Assessment

**Low Risk** - Simple utility function move with no business logic changes.

**Rollback Plan**: Easy to revert by recreating service class if needed.