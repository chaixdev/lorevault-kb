# LVREF002: Move PromptLoaderService into LLM Adapters

**Priority**: Medium  
**Effort**: 4 hours  
**Risk**: Low  
**Phase**: 1 (Eliminate Utility Services)  
**Dependencies**: None

## Problem Statement

`PromptLoaderService` is just loading text files from resources. This is infrastructure concern that belongs in the LLM adapters, not as a separate business service.

## Current State

```java
@Service
public class PromptLoaderService {
    public PromptTemplate getSceneDetectionPass1PromptTemplate() {
        // Load file from classpath, return template
    }
}

// LLM services depend on this utility
@Autowired PromptLoaderService promptLoader;
```

## Target State

```java
// Create an infrastructure PromptRepository with caching & config-driven paths
package com.lorevault.api.infra.prompt;

@Component
public class ClasspathPromptRepository implements PromptRepository {
    private final PromptCache cache; // simple in-memory cache with TTL (configurable)
    private final PromptLocationResolver locationResolver; // resolves logical names > resource paths

    public String get(String name) {
        return cache.getOrLoad(name, () -> loadFromClasspath(locationResolver.resolvename)))
    }
}

// Adapter depends on repository, uses lazy load (avoid heavy @PostConstruct)
@Component
public class OpenAiSceneDetector implements SceneDetectionPort {
    private final PromptRepository prompts;

    public DetectionResult detectScenes(String text) {
        String tmpl = prompts.get("scene-detection-pass1");
        // use tmpl ...
    }
}
```

## Implementation Steps

1. Introduce `PromptRepository` interface in infra layer
2. Implement `ClasspathPromptRepository` with small cache and config-driven base path
3. Inject `PromptRepository` into LLM adapters; load prompts lazily at call time
4. Support optional dev hot-reload (disabled in prod) via last-modified checks
5. Remove `PromptLoaderService` class and its DI usage
6. Add configuration properties (e.g., `lorevault.prompts.base-path`, TTL)

## Acceptance Criteria

- [ ] No `PromptLoaderService` class exists
- [ ] `PromptRepository` introduced and used by LLM adapters
- [ ] Prompts are loaded lazily with caching; no heavy `@PostConstruct`
- [ ] Config-driven prompt locations and TTL are supported
- [ ] All LLM functionality works identically; tests green

## Files to Modify

**Files to DELETE**:

- `PromptLoaderService.java`
- `PromptLoaderServiceTest.java`

**Files to CREATE**:

- `PromptRepository.java` (infra)
- `ClasspathPromptRepository.java`
- `PromptCache.java` (simple cache abstraction)
- `PromptLocationResolver.java`

**Files to UPDATE**:

- `OpenAiSceneDetector.java` - Inject and use `PromptRepository`
- Other LLM adapter classes - Switch to repository
- Configuration classes - Add `lorevault.prompts.*` properties

## Testing Strategy

- Focus on adapter behavior using a fake `PromptRepository`
- Unit-test `ClasspathPromptRepository` caching and fallback behavior
- Configuration tests for base path resolution and TTL
- If hot-reload enabled in dev, add a lightweight test for change detection

## Risk Assessment

**Low Risk** - Moving infrastructure concern to appropriate layer.
