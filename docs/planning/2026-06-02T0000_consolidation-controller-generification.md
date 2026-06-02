# Consolidation Controller Generification

**Status:** PLANNING  
**Created:** June 2, 2026  
**Source:** P4 Deep Review — HIGH-2 finding

## Problem

10 consolidation controllers (~830 lines) are ~95% structurally identical. Differences are mechanically derivable from `{scope=book|chapter, entityType=object|location|concept|collective|individual|event}`. UUID parsing, validation, not-found checks, log lines, `StageExecutionContext` construction, `StepExecutionResponse` construction, and event publishing are identical boilerplate repeated 10×. Additionally, the 3 existing `@WebMvcTest` tests follow the same copy-paste pattern.

This duplication also compounds the repository-injection layering violation (CRIT-2) — all 10 controllers directly inject `ChapterGraphRepository` or `BookGraphRepository`.

**Affected files:**
- `Chapter{Individual,Collective,Concept,Location,Object,Event}ConsolidationCommandController.java` (6)
- `Book{Individual,Collective,Concept,Location,Object}ConsolidationCommandController.java` (5)
- `StepExecutionCommandController.java` (partially — 4 of its methods follow the same pattern)

## Design Question: Abstract Generics vs Functional Config

### Option A: Abstract Generic Base Class

```java
abstract class BaseConsolidationController {
    // shared: UUID parsing, validation, execute, event publishing, error response
    protected ResponseEntity<?> executeStep(UUID chapterId, boolean fireEvents) { ... }
}

@RestController
@RequestMapping("/api/command/ingest/chapters/{chapterId}/consolidate-individuals")
class ChapterIndividualConsolidationCommandController extends BaseConsolidationController {
    ChapterIndividualConsolidationCommandController(ChapterIndividualConsolidationOperation op, ...) { super(op, ...); }
    @PostMapping ResponseEntity<?> consolidate(@PathVariable String chapterId, @RequestParam boolean fireEvents) { ... }
}
```

**Pros:**
- Natural Spring MVC idiom (`@RequestMapping` on concrete classes)
- IDE navigation works (go-to-definition lands on actual controller)
- Easy to add lane-specific overrides
- Familiar to Java/Spring devs

**Cons:**
- Still requires 11+ concrete subclasses (one per endpoint path)
- Each subclass is ~15 lines of annotation + constructor delegation
- Inheritance carries testing overhead

### Option B: Functional Config Record

```java
record ConsolidationConfig(
    String pathSegment, StageKey stageKey, StepKey stepKey,
    Function<ConsolidationOperation, StageOperation> operationExtractor
) {}

// Single controller with programmatic routing or 10 @PostMapping methods
```

**Pros:**
- Zero subclass boilerplate
- Single controller class
- Config records are trivially serializable/testable

**Cons:**
- No clean way to map 10 different URL paths to a single Spring controller without reflection hacks OR ending up with one controller containing 10 `@PostMapping` methods (same verbosity)
- `Function<>` fields in config records complicate Spring bean wiring
- Less familiar to typical Java/Spring devs

### Recommendation

Option A (Abstract Generic Base Class) is preferred. The functional approach doesn't actually reduce endpoint count — Spring MVC requires one `@RequestMapping`-annotated method per URL path, so you either have 11 subclasses or 1 class with 11 methods. The abstract base eliminates the boilerplate *inside* each method, but the endpoint declarations remain. The abstract class approach makes this natural: each subclass declares its `@RequestMapping` and delegates to `super.executeStep(...)` with its type-specific config.

## Scope

1. Extract `BaseConsolidationController` with shared logic (UUID parsing, validation, `StageOperation.execute()`, event publishing, error response building)
2. Each concrete subclass becomes `@RequestMapping` annotation + `super` delegation (~10-15 lines each)
3. Resolve CRIT-2 simultaneously: move `findById` existence checks into the service layer — the base controller should only call `stageOperation.execute()` and map the result
4. Replace 3 copy-paste `@WebMvcTest` test classes with a single parameterized test class
5. Partially address CRIT-6/HIGH-10: the base controller pattern makes it trivial to add proper `StageOperation` delegation to `UiOperatorActionsController`

## Expected reduction

| Before | After |
|--------|-------|
| ~830 lines (10 controllers) | ~150 lines (1 base class + 11 thin subclasses) |
| 3 test classes (~250 lines) | 1 parameterized test class (~100 lines) |

## Blockers

None. All immediate CRITICAL/HIGH fixes from P4 review are applied. This refactor is the next step and is self-contained.

## Related

- [P4 Web & REST Layer Review](../reviews/2026-06-02T0000_p4-web-rest-layer-review.md) — CRIT-2, HIGH-2, HIGH-9, HIGH-10
- [Coding Standards](../../docs/rules/coding-standards.md) — Over-Abstraction, Lombok Discipline
