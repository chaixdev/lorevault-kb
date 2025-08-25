# Service Design Principles - LoreVault LLM Guidance

**Purpose**: Guidelines for creating appropriately-sized services in ports & adapters architecture.

**Anti-Pattern**: Over-segmentation driven by misapplied "single responsibility" principle.

## Core Principles

### ✅ **Create Services For Business Capabilities**

Services should represent **user-facing business operations**, not internal implementation details:

```java
// ✅ GOOD: Business capability
@Service
class IngestionService {
    public SubmitChapterResponse submitChapter(request) {
        // Coordinates: validation + job creation + AI processing + persistence
    }
}

// ❌ BAD: Internal implementation detail
@Service  
class ChapterValidationService {
    public boolean isValid(chapter) { ... }  // Just a method call!
}
```

### ✅ **Only Extract for External Dependencies**

Create separate services only when you need to abstract **external systems**:

```java
// ✅ GOOD: External boundary (LLM API)
interface SceneDetectionPort {
    List<Scene> detectScenes(String text);
}

// ❌ BAD: Internal logic (no external boundary)
interface ChapterValidationPort {
    ValidationResult validate(Chapter chapter);
}
```

### ✅ **Keep Related Logic Together**

Don't split services that work on the same data or business process:

```java
// ✅ GOOD: Job management grouped together
@Service
class IngestionJobService {
    public IngestionJob createJob(chapterId) { ... }
    public JobStatus getStatus(jobId) { ... }
    public List<IngestionJob> listJobs(filter) { ... }
    public void updateStatus(jobId, status) { ... }
}

// ❌ BAD: Artificially split for no benefit
@Service class IngestionJobLifecycleService { /* just create/update */ }
@Service class JobQueryService { /* just read operations */ }
```

## Service Size Guidelines

### **Ideal Service Size**
- **100-300 lines**: Sweet spot for most services
- **5-10 public methods**: Cohesive set of related operations
- **Single aggregate/entity focus**: One primary domain concept

### **Warning Signs of Over-Segmentation**
- Services with **1-3 methods** that just delegate
- Services that are **always called together**
- Services that **share the same dependencies**
- Method names that repeat the service name: `ValidationService.validate()`

### **Warning Signs of Under-Segmentation**  
- Services with **500+ lines** or **15+ public methods**
- Services that touch **multiple external systems** directly
- Services that handle **multiple unrelated business capabilities**

## LLM Implementation Guidelines

### **When Asked to Implement a Feature**

1. **Start with ONE service** that handles the complete business operation
2. **Only split when you encounter a REAL external boundary** (database, API, etc.)
3. **Prefer private methods** over new services for internal logic
4. **Ask yourself**: "Would a user understand this service name as a complete operation?"

### **Refactoring Existing Over-Segmented Code**

When consolidating services:

1. **Identify service clusters** that always work together  
2. **Merge into the primary business operation service**
3. **Convert extracted services to private methods**
4. **Keep only the ports that represent real external boundaries**

### **Testing Strategy for Consolidated Services**

Larger services are **easier to test**, not harder:

```java
@Test
class IngestionServiceTest {
    @Mock ContentPersistencePort persistencePort;  // External boundary
    @Mock SceneDetectionPort sceneDetectionPort;   // External boundary
    
    @Test  
    void submitChapter_ShouldHandleCompleteWorkflow() {
        // Test the entire business operation in one test
        // This is MORE valuable than testing tiny pieces separately
    }
}
```

## Examples: Right vs Wrong Service Boundaries

### ✅ **GOOD: Business-Focused Services**

```java
@Service IngestionService          // "Submit chapters for processing"
@Service SearchService             // "Find content by query"  
@Service SystemHealthService       // "Check system status"
@Service ContentService            // "Retrieve stored content"
```

### ❌ **BAD: Implementation-Focused Services**

```java
@Service ChapterValidationService     // Just validation logic
@Service HashService                 // Just a utility function  
@Service JobQueryService             // Just database queries
@Service PromptLoaderService         // Just file loading
@Service ScenePersistenceService     // Just save operations
```

## Decision Framework

**Before creating a new service, ask:**

1. **"Is this a complete business capability that a user would recognize?"**
   - ✅ Yes → Consider a service
   - ❌ No → Make it a method

2. **"Does this represent a real external system boundary?"**  
   - ✅ Yes → Need a port + adapter  
   - ❌ No → Internal logic only

3. **"Would this service have any value if used independently?"**
   - ✅ Yes → Probably appropriate
   - ❌ No → Merge with related functionality

4. **"Is this more than just moving existing code to a new class?"**
   - ✅ Yes → Adds real abstraction value
   - ❌ No → Keep as private methods

## Architecture Pattern Summary

```
┌─────────────────────────────────────┐
│           Web Controllers           │ ← Thin HTTP adapters
├─────────────────────────────────────┤  
│        Business Services            │ ← 4-6 focused services
│   (100-300 lines each)            │   (complete operations)
├─────────────────────────────────────┤
│             Ports                   │ ← Only for external systems
│    (Database, LLM APIs, etc.)      │
├─────────────────────────────────────┤
│            Adapters                 │ ← Implement ports
│     (Neo4j, OpenAI, etc.)          │
└─────────────────────────────────────┘
```

## Key Takeaway

**Services should map to user stories, not implementation details.**

- ✅ "As a user, I want to submit a chapter" → `IngestionService`
- ❌ "As a developer, I want to validate input" → Just a method call

This approach produces **fewer, more focused services** that are **easier to understand, test, and maintain**.