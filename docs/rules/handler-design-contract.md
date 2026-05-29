# Handler Design Contract

**Status:** Active

LoreVault ingestion handlers must be designed around retry safety, explicit ownership, and dependency-aware invalidation.

Do not describe a handler as "idempotent" unless it is strictly idempotent: running it more than once has the same effect as running it once. Most pipeline work should instead be held to a **retry-safe handler contract**.

## Required Rules

### 1. Declare ownership

Every ingestion handler must have a clear ownership scope:

- owned nodes
- owned relationships
- owned projection scope
- upstream dependencies
- downstream dependents

A handler may replace or invalidate only its owned output. It must not casually delete upstream evidence or downstream projections owned by another stage.

### 2. Success events require coherent output

A success-shaped event must mean downstream handlers can safely start from the emitted state.

For example, a `Book*ReducedEvent` must mean the relevant book-level projection is coherent for that lane. It must not mean merely that the handler woke up, observed contention, skipped work, or exhausted a retry budget.

### 3. Failure is not alternate success

Retryable or deferred conditions must not be represented by downstream success events.

Examples:

- claim contention
- transient LLM/provider failure
- partial write failure
- dependency not yet current
- retry budget exhaustion that should be requeued or escalated

Use retry/deferred/failure handling instead of publishing completion-barrier events for these cases.

### 4. Invalidation must propagate

When a handler invalidates or replaces its owned output, dependent stages must be invalidated, rebuilt, or prevented from being treated as current.

The dependency cost increases upstream:

- replacing mention/evidence output can invalidate chapter and book projections
- replacing chapter projections can invalidate book projections
- replacing book projections should stay local to that book-level lane projection

### 5. Projection replacement must be coherent

Do not delete an active projection in an earlier committed transaction and rebuild it later as a routine pipeline side effect.

Use one of these patterns instead:

- atomic scoped replacement where delete/save/link commit together
- staged replacement plus activation/supersession
- explicit invalidation state that prevents stale dependents from being treated as current

Long-running analysis, including LLM calls, must not be wrapped in database transactions merely to satisfy this rule. Do analysis first, then perform a small coherent write/activation step.

### 6. Manual reruns are pipeline operations

Manual command endpoints must follow the same ownership, invalidation, and event semantics as event-driven retries.

Before adding a rerun endpoint, document:

- what projection is recomputed
- what downstream projections become stale
- whether downstream events are emitted
- how concurrent reruns for the same scope are handled
- how retryable/deferred/terminal outcomes are surfaced

## Required Handler Contract

Every new ingestion handler, and every substantial handler behavior change, must provide a contract using this structure:

```md
## Handler Contract: <HandlerName>

- Trigger event:
- Owned nodes:
- Owned relationships:
- Owned projection scope:
- Upstream dependencies:
- Downstream dependents:
- Retry/replay behavior:
- Failure behavior:
- Invalidation behavior:
- Completion event meaning:
- Safe manual rerun conditions:
```

The contract can live in a focused pattern/spec doc, an ADR when the shape is architectural, or the implementation proposal while a design is still being worked through. Accepted behavior must be promoted into canonical docs.

## Review Standard

A handler change is not merge-ready when reviewers cannot answer:

- what does this handler own?
- what does its success event allow downstream handlers to assume?
- what stale outputs are created if this handler is retried or manually rerun?
- what happens on duplicate event delivery?
- what happens after a partial failure?
- what distinguishes completed-empty, deferred, retryable failure, and terminal failure?

Related pattern: [Handler Retry-Safety Pattern](../patterns/ingestion/handler-retry-safety.md).

### 7. `execute(StageExecutionContext ctx)` must not publish domain events

When the `StageOperation` interface's `execute(StageExecutionContext ctx)` method is called, it must not publish domain events. Event emission is the caller's responsibility — either the `StageDispatcher` or the REST controller via `StepEventMapper`.

This ensures that direct `execute(ctx)` calls from step endpoints don't trigger downstream cascades unless explicitly requested via `fireEvents=true`.

The `StageDispatcher` (or `@EventListener` adapter) delegates to `execute(ctx)` and handles event publication. The REST controller calls `execute(ctx)` directly and publishes events conditionally based on the `fireEvents` parameter.

### 8. Handlers must not re-validate domain preconditions owned by services

The service that performs an operation owns its domain constraints: null checks, empty-input guards, validation logic. Handlers must not duplicate these checks before calling the service.

```java
// Wrong — handler re-checks a precondition the service already enforces
String chapterText = chapter.getRawText();
if (chapterText == null || chapterText.trim().isEmpty()) {
    return List.of();
}
var outcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);

// Correct — trust the service; check the return value if you need to know
var outcome = sceneDetectionService.detectScenesInChapter(jobId, chapter);
if (outcome.scenes().isEmpty()) {
    log.info("No scenes detected for chapter {}", chapterId);
}
```

If the service's guard behavior changes, every handler-side copy is a drift point. If a handler needs to know "did this produce anything?", check the return value — not the inputs.

This applies equally to factory invariants. If `Chapter.createWithReferences()` guarantees an ID is set, the caller must not guard against a null ID — that masks a factory defect.

### 9. Handlers must thread `StageExecutionContext` through to services

Every `StageOperation` handler receives `StageExecutionContext ctx` in its `execute(ctx)` method. This context must be passed as the first parameter to any service method that creates or persists domain nodes.

```java
// Required — thread ctx through to services
@Override
public StepResult execute(StageExecutionContext ctx) {
    chapterIndividualConsolidationService.consolidateChapter(ctx, chapterId);
    // ...
}

// Wrong — ctx available but not passed
@Override
public StepResult execute(StageExecutionContext ctx) {
    chapterIndividualConsolidationService.consolidateChapter(chapterId); // no ctx
}
```

Services that create domain nodes must accept `StageExecutionContext ctx` as their first parameter and use `ctx.stageId()` when constructing entities. This ensures every node carries provenance for cleanup and replay.

See ADR-014 (explicit parameter threading over ThreadLocal).

### 10. Domain nodes must carry `stageId` provenance

Every `@Node` entity that is created during pipeline execution must include a `@Property("stageId") UUID stageId` field. This enables:

- **Stage-scoped cleanup:** `deleteDataByStageId(stageId)` removes all nodes and relationships created by a specific stage execution.
- **Stage replay:** Delete the previous stage's output, then re-run the stage.
- **Audit:** Every domain node can be traced back to the `Stage` node that created it.

For Java records, `stageId` is a record component. For `@Data` classes (Scene, Chunk), it is a field with a setter — do not add it to the `@PersistenceCreator` constructor.

```java
// Record entity — stageId as record component
public record ChapterIndividual(
        @Id UUID id,
        UUID chapterId,
        @Property("stageId") UUID stageId,  // after the scope ID
        String displayName,
        // ...
) {}

// @Data entity — stageId as field with setter
@Data
@Node("Scene")
public class Scene {
    @Property("stageId")
    private UUID stageId;  // set via scene.setStageId(ctx.stageId())
    // ...
}
```

Placement convention: `stageId` goes after the scope ID (`chapterId` for chapter entities, `bookId` for book entities, `sceneId` for mention entities) and before business fields.

See ADR-015 (stage node provenance over StageOutput nodes).

### 11. Handlers must use `@ForStage` annotation

Every handler must be annotated with `@ForStage(StageKey.X)` and implement `StageOperation`. The `StageDispatcher` discovers handlers by this annotation at startup and routes `StageTriggeredEvent` to the correct handler.

```java
// Required
@ForStage(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION)
@Component
public class ChapterIndividualConsolidationHandler implements StageOperation {
    @Override
    public StepResult execute(StageExecutionContext ctx) { ... }
}
```

The dispatcher validates at startup that every `StageKey` has exactly one handler. Missing or duplicate registrations produce a fail-fast `IllegalStateException`.

See ADR-013 (coordinator/dispatcher over independent async listeners).
