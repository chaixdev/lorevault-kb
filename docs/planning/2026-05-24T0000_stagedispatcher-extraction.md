# StageDispatcher Extraction

**Date:** May 24, 2026
**Status:** Design — ready for implementation review
**Parent:** [Submission Flow Code Quality Cleanup](2026-05-23T1530_submission-flow-cleanup.md) (issue #7 + #20)
**Oracle reviewed:** May 24, 2026 — direction confirmed correct, adjustments incorporated.

Centralizes the 13 handler `onTrigger` boilerplate into a single `StageDispatcher` bean. Handlers become pure domain objects — just `execute(jobId, chapterId)` with no `@Async`, `@EventListener`, or orchestration fields.

---

## Current State: 13 Identical onTrigger Patterns

Every handler repeats the same 4-phase structure:

```java
@Async("someTaskExecutor")
@EventListener
public void onTrigger(StageTriggeredEvent event) {
    // 1. Guard — atomic TRIGGERED→RUNNING transition
    if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) return;

    // 2. Idempotency — skip if already completed
    if (stageOutputRepo.existsByChapterIdAndStep(chapterId, event.getStage())) {
        stageRepo.setSkipped(jobId, event.getStage());
        eventPublisher.publishEvent(new StageCompletedEvent(/* skip */));
        return;
    }

    // 3. Execute
    StepResult result = execute(jobId, chapterId);

    // 4. Emit completion
    eventPublisher.publishEvent(new StageCompletedEvent(/* result */));
}
```

Two variants exist:
- **Chapter-level** (9 handlers): idempotency via `existsByChapterIdAndStep`, execute takes `(jobId, chapterId)`
- **Book-level** (4 handlers): idempotency via `existsByBookIdAndStep`, execute takes `(jobId, bookId)`

Each handler injects 4 orchestration fields: `StageGraphRepository`, `StageOutputGraphRepository`, `ApplicationEventPublisher`, `PipelineStageSupport`. Each constructs `PipelineStageSupport` from `IngestionJobService` + `ApplicationEventPublisher`.

**Total:** 13 × 4 orchestration fields + 13 × `IngestionJobService` (for `PipelineStageSupport` construction) = 65 injection points of orchestration boilerplate, plus 13 copies of the identical guard+idempotency+emit pattern.

---

## Target State

```java
@Component
public class StageDispatcher {
    private final Map<StageKey, StageOperation> handlers;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Async
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        MDC.put("stage", event.getStage().name());
        MDC.put("jobId", event.getJobId().toString());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            StageOperation handler = handlers.get(event.getStage());
            if (handler == null) {
                log.error("No handler registered for stage: {}", event.getStage());
                return;
            }

            // 1. Guard
            if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) return;

            UUID jobId = event.getJobId();
            UUID chapterId = event.getChapterId();
            UUID bookId = event.getBookId();

            // 2. Idempotency (chapter-level or book-level)
            if (isChapterStage(event.getStage())) {
                if (stageOutputRepo.existsByChapterIdAndStep(chapterId, event.getStage())) {
                    stageRepo.setSkipped(jobId, event.getStage());
                    emitComplete(jobId, chapterId, null, event.getStage(),
                            StepResult.success(event.getStage().name(), "Skipped — already completed", 0L));
                    return;
                }
            } else {
                if (bookId != null && stageOutputRepo.existsByBookIdAndStep(bookId, event.getStage())) {
                    stageRepo.setSkipped(jobId, event.getStage());
                    emitComplete(jobId, chapterId, bookId, event.getStage(),
                            StepResult.success(event.getStage().name(), "Skipped — already completed", 0L));
                    return;
                }
            }

            // 3. Execute — with error boundary
            DispatchContext ctx = new DispatchContext(jobId, chapterId, bookId, event.getStage());
            StepResult result;
            try {
                result = handler.execute(ctx);
            } catch (Exception e) {
                log.error("Unhandled exception in stage {}: jobId={}", event.getStage(), jobId, e);
                result = StepResult.failure(event.getStage().name(),
                        ExceptionSanitizer.sanitizeMessage(e), 0L);
            }

            // 4. Emit
            emitComplete(jobId, chapterId, bookId, event.getStage(), result);

        } finally {
            sample.stop(Timer.builder("ingestion.stage.duration")
                    .tag("stage", event.getStage().name()).register(meterRegistry));
            MDC.clear();
        }
    }
}
```

### StageOperation interface

```java
@FunctionalInterface
public interface StageOperation {
    StepResult execute(DispatchContext ctx);
}
```

### DispatchContext

Simple value object carrying what every handler needs:

```java
public record DispatchContext(UUID jobId, UUID chapterId, UUID bookId, StageKey stage) {}
```

Handlers extract the IDs they care about. Book-level handlers use `ctx.bookId()`; chapter-level handlers use `ctx.chapterId()`.

---

## Handler Changes

Each handler loses: `onTrigger()`, `@Async`, `@EventListener`, `StageGraphRepository`, `StageOutputGraphRepository`, `ApplicationEventPublisher`, `PipelineStageSupport`, `IngestionJobService`.

Each handler gains: implements `StageOperation`, `execute(DispatchContext ctx)` replaces `execute(UUID jobId, UUID chapterId)`.

**Before (chapter-level):**
```java
@Component @Slf4j
public class ChapterIndividualResolutionHandler implements ChapterIndividualResolutionOperation {
    private final ChapterIndividualResolutionService service;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;
    // 6 dependencies, 4 orchestration

    @Async("ingestionLaneTaskExecutor") @EventListener
    public void onTrigger(StageTriggeredEvent event) { /* 25-line boilerplate */ }

    public StepResult execute(UUID jobId, UUID chapterId) { /* domain logic */ }
}
```

**After:**
```java
@Component
public class ChapterIndividualResolutionHandler implements StageOperation {
    private final ChapterIndividualResolutionService service;
    // 1 dependency, 0 orchestration

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        // domain logic unchanged
    }
}
```

**Effect per handler:** drops from 6-18 dependencies to 1-14. `SceneDetectionHandler` drops from 18 fields to 14. PipelineStageSupport injection eliminated from all 13 handlers. `IngestionJobService` injection eliminated from all 13 handlers.

---

## CRITICAL: Transaction and Error Boundaries

### Transaction boundary: do NOT annotate onTrigger with @Transactional

Reasons:
1. LLM calls in handlers can take 30-120s — holding a transaction that long exhausts the connection pool and creates lock contention
2. Each handler manages its own `@Transactional` boundaries via persistence services
3. Nested transaction semantics (REQUIRES_NEW in delegates) interact poorly with dispatcher-level transactions
4. `@Async` + `@Transactional` are incompatible — Spring's transactional proxy and async proxy can't coexist on the same method without explicit configuration

**Code comment required:**
```java
/**
 * This method must NOT be @Transactional.
 * Each handler manages its own transaction boundaries via its delegate services.
 * LLM calls may take 30-120s — holding a dispatcher-level transaction
 * across that duration would exhaust the Neo4j connection pool.
 */
```

### Error boundary: catch unchecked exceptions from handler.execute()

If a handler's `execute()` throws an unchecked exception (NPE, etc.), the dispatcher must:
1. Catch it
2. Convert to `StepResult.failure()`
3. Emit `StageCompletedEvent` with failure

**Without this:** the stage hangs RUNNING until the 300s stale recovery window. This is a correctness requirement, not optional.

```java
try {
    result = handler.execute(ctx);
} catch (Exception e) {
    log.error("Unhandled exception in stage {}: jobId={}", event.getStage(), jobId, e);
    result = StepResult.failure(event.getStage().name(),
            ExceptionSanitizer.sanitizeMessage(e), 0L);
}
```

**Corollary:** The `execute()` contract in each handler must be audited to ensure it either:
- Returns `StepResult.failure()` for expected errors (current behavior — all handlers catch Exception and return failure)
- Let's the dispatcher catch unexpected errors (NPE, OOM, etc.)

No handler currently relies on the dispatcher error boundary — they all have their own try-catch. So the dispatcher error boundary is a safety net for unhandled exceptions, not a replacement for handler-level error handling.

---

## StageKey → handler registration

Handlers self-register via annotation or post-construct:

**Option A — Self-registration via `@ForStage`:**
```java
@ForStage(SCENE_SEGMENTATION)
@Component
public class SceneDetectionHandler implements StageOperation { ... }
```

The dispatcher auto-discovers all `@ForStage` beans and builds the `Map<StageKey, StageOperation>`.

**Option B — Spring collection injection:**
```java
public StageDispatcher(List<StageOperation> handlers) {
    this.handlers = new EnumMap<>(StageKey.class);
    for (StageOperation h : handlers) {
        ForStage anno = h.getClass().getAnnotation(ForStage.class);
        if (anno != null) handlers.put(anno.value(), h);
    }
}
```

**Recommendation:** Option A — self-documenting, fail-fast if two handlers claim the same stage, no hidden registration logic.

---

## MDC: unified logging

Before: 13 different log prefix styles (`[SCENE_DETECTION]`, `[LANE:CONTENT] [CHUNKING]`, `[SKIPPED]`, etc.) with inconsistent formatting.

After: dispatcher sets MDC context before execution:
```java
MDC.put("stage", event.getStage().name());
MDC.put("jobId", event.getJobId().toString());
```

All nested service logs (LlmClient, SceneDetectionService, persistence services) automatically carry `stage` context without manual prefixing. Log aggregation (ELK, Datadog) can filter/group by stage without regex parsing.

Handler-specific log statements should remove `[PREFIX]` annotations and rely on MDC — the log pattern includes `%X{stage}` and `%X{jobId}`.

---

## Micrometer: unified timing

Before: 13 handlers × manual `System.currentTimeMillis()` timing, 13 different elapsed reporting styles, no metric aggregation.

After: single `Timer.Sample` in the dispatcher with `stage` tag:
```java
Timer.builder("ingestion.stage.duration")
    .tag("stage", event.getStage().name())
    .register(meterRegistry);
```

Produces per-stage duration percentiles (p50/p95/p99) in Grafana/Datadog without per-handler instrumentation.

Handler-level elapsed timers (for `StepResult` construction) can keep `System.currentTimeMillis()` — the Micrometer timer is for observability, `StepResult` elapsed is for the coordinator.

---

## Handler Inventory

| Handler | execute() signature | Orchestration fields removed |
|---------|---------------------|------------------------------|
| `SceneDetectionHandler` | `(jobId, chapterId)` → uses bookId internally | 4 |
| `ChunkingHandler` | `(jobId, chapterId)` | 4 |
| `EmbeddingHandler` | `(jobId, chapterId)` | 4 |
| `ChapterIndividualResolutionHandler` | `(jobId, chapterId)` | 4 |
| `ChapterCollectiveResolutionHandler` | `(jobId, chapterId)` | 4 |
| `ChapterLocationResolutionHandler` | `(jobId, chapterId)` | 4 |
| `ChapterObjectResolutionHandler` | `(jobId, chapterId)` | 4 |
| `ChapterEventResolutionHandler` | `(jobId, chapterId)` — handles 3 sub-stages internally | 4 |
| `ChapterEventEmbeddingHandler` | `(jobId, chapterId)` | 4 |
| `BookIndividualReductionHandler` | `(jobId, bookId)` | 4 |
| `BookCollectiveReductionHandler` | `(jobId, bookId)` | 4 |
| `BookLocationReductionHandler` | `(jobId, bookId)` | 4 |
| `BookObjectReductionHandler` | `(jobId, bookId)` | 4 |

**Note on `ChapterEventResolutionHandler`:** Handles `CHAPTER_EVENT_RESOLUTION` but also gates on sub-stages internally. This does NOT break the dispatcher pattern — the dispatcher dispatches to `execute()`, and `execute()` manages its own sub-stage logic internally. No change needed to the handler.

**Total orchestration fields removed:** 52 (13 × 4) + 13 `IngestionJobService` (for `PipelineStageSupport` construction) = 65 injection points eliminated.

**Files deleted:** `PipelineStageSupport.java` (after #12)

**New files:** `StageDispatcher.java`, `StageOperation.java`, `DispatchContext.java`, `ForStage.java` (annotation)

---

## Prerequisites (blocking)

- [ ] **Issue #18:** Complete walkthrough of remaining 12 handlers — verify all follow the identical pattern
- [ ] **Issue #12:** Delete `PipelineStageSupport` (or at minimum: extract `sanitizeExceptionMessage` to utility, remove all 16 `updateJobStatus` call sites, remove all `PipelineStageSupport` dependencies from handlers)
- [ ] **Quick wins complete:** Handlers must be free of `stageSupport.updateJobStatus()` calls before the dispatcher can be introduced
- [ ] **Test impact:** All handler tests reference `PipelineStageSupport` and mock `StageGraphRepository`/`StageOutputGraphRepository`/`ApplicationEventPublisher` — these must be updated

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Dispatcher mis-handles stage → DAG deadlock | High | Integration test: end-to-end chapter ingestion before/after. Verify all 15 stages trigger and complete. |
| Unchecked exception from handler → stage hangs RUNNING | High | Error boundary catches and converts to failure. Covered by design. |
| Chapter-level vs book-level idempotency mismatch | Medium | Dispatcher checks `isChapterStage()` / `isBookStage()` based on `StageKey` enumeration. Hardcoded mapping — fail-fast if a new stage doesn't match. |
| Async executor qualifier mismatch | Medium | Currently handlers use 2 different executors (`sceneDetectionTaskExecutor` and `ingestionLaneTaskExecutor`). Dispatcher uses a single executor. **Decision needed:** one executor for all, or per-stage executor routing? |
| `StageOperation` interface breaks handler `execute()` callers | Low | `StepExecutionCommandController` calls `handler.execute()` directly — switch to `new DispatchContext(...)` |

### Open question: single vs per-stage executors

Currently handlers use two executors:
- `sceneDetectionTaskExecutor` — SceneDetectionHandler only
- `ingestionLaneTaskExecutor` — all other handlers

If the dispatcher uses a single executor, the SceneDetectionHandler loses its dedicated thread pool. Check whether this is intentional (scene detection was historically resource-intensive and needed isolation) — if so, the dispatcher must route stages to their designated executors:

```java
private TaskExecutor executorFor(StageKey stage) {
    return switch (stage) {
        case SCENE_SEGMENTATION -> sceneDetectionTaskExecutor;
        default -> ingestionLaneTaskExecutor;
    };
}
```

Or: keep the `@Async` annotation on the dispatcher but route the actual execution to the correct executor programmatically. This is simpler than per-stage `@EventListener` but requires verifying `@Async` qualifier routing works with programmatic executor selection.

---

## Test Coverage Gap (May 25, 2026)

The Phase 1 cleanup deleted 20 stale test files that tested the old event-driven handler API (see [quick wins implementation notes](2026-05-24T0000_submission-cleanup-quick-wins.md)). These covered:

| Category | Deleted | What they tested | Replacement needed |
|----------|---------|------------------|--------------------|
| Old domain model tests | 6 | `IngestionJob`, `StatusRecord`, `IngestionJobGraphRepository` | Already covered by `StageGraphRepository` tests (new model) |
| Handler `handleXxxEvent` tests | 14 | Direct event-listener invocation on 13 pipeline handlers | Per-handler `onTrigger(StageCompletedEvent, StageKey)` tests |

The 14 deleted handler tests represented the bulk of pipeline-stage test coverage. They validated:
- Happy path: handler publishes correct completion event
- Failure path: handler publishes `IngestionFailedEvent` on exceptions
- Edge cases: empty input, duplicate processing, missing entities

**Coverage parity must be restored before merging the StageDispatcher.** Specifically:

1. **`StageDispatcher` unit test** — verify it routes each `StageKey` → correct executor, catches handler exceptions, and emits `StageCompletedEvent` with success/failure.

2. **Per-handler `onTrigger` tests** — rewrite the 14 deleted tests against the new `onTrigger` API. Each should:
   - Construct a `StageCompletedEvent` (or `StageTriggeredEvent`) with the correct `StageKey`
   - Call `handler.onTrigger(jobId, chapterId)` (or bookId for book-level)
   - Verify `Stage` transitions in `StageGraphRepository` (idempotency, skip, complete, fail)
   - Verify downstream event publishing

3. **Integration test** — end-to-end: submit chapter → dispatcher triggers scene detection → handler executes → `StageCompletedEvent` emitted → next stage triggered.

**Estimated additional effort:** ~60 minutes (test writing). This should be part of the Phase 3 StageDispatcher PR — the tests serve as both validation and documentation of the dispatcher contract.

**Phase 3 — structural change, standalone PR.**

Dependencies: Quick wins (Phase 1) + PipelineStageSupport removal (Phase 2) must be complete. The dispatcher cannot coexist with handlers that still have their own `@Async`/`@EventListener`/`onTrigger`.

**Estimated effort:** ~150 minutes (design + implementation + test coverage parity). Includes ~60 minutes to rewrite the 14 deleted handler tests against the `onTrigger` API.

---

## Test Suite Design (May 28, 2026)

Following the testing guidance in `docs/rules/developer-testing-workflow.md`, the test suite is organized by layer:

### Layer Classification

| Test Suite | Layer | Rationale |
|------------|-------|-----------|
| StageDagTest | **Business logic** | Pure data structure with algorithmic traversal (BFS, topological sort, connectivity validation) |
| StageKeyTest | **Business logic** | Enum classification methods encode pipeline semantics |
| StepResultTest | **Business logic** | Factory methods with validation logic |
| StageDispatcherTest | **Business logic** | Guard, idempotency, error boundary, MDC, completion emission |
| IngestionPipelineCoordinatorTest | **Business logic** | DAG barrier evaluation, bootstrap, recovery |
| StageDispatcherWiringTest | **Integration** | Spring context wiring |
| PipelineEndToEndTest | **Integration** | Full event loop across modules |

### Cuts (Testing Compiler)

The following were cut because they test record accessors and annotation metadata — no runtime behaviour:

- **DispatchContextTest** — record accessors
- **ForStageTest** — annotation metadata
- **StageOperationContractTest** — interface contract

Add tests only when runtime behaviour is added (validation, guards).

### Mock vs Fake vs Stub Strategy

| Test Suite | Strategy | Rationale |
|------------|----------|-----------|
| StageDispatcherTest | **Mock** | Verify interactions: `stageRepo.setRunningConditionally()`, `stageOutputRepo.existsByChapterIdAndStep()`, `eventPublisher.publishEvent()` |
| IngestionPipelineCoordinatorTest | **Mock** | Verify interactions: `stageRepo.tryTrigger()`, `eventPublisher.publishEvent()` |
| StageDispatcherWiringTest | **Stub** | Spring context needs beans, tests don't exercise them |
| PipelineEndToEndTest | **Stub** | Configurable stub handlers registered via `@TestConfiguration` |

### Revised Test Count

| Suite | Count | Notes |
|-------|-------|-------|
| StageDagTest | ~18 | Pure data structure, all justified |
| StageKeyTest | ~6 | Blocked on prerequisite (extract classification to enum) |
| StepResultTest | ~8 | Factory methods with validation |
| StageDispatcherTest | ~28 | Business logic with mocks |
| IngestionPipelineCoordinatorTest | ~25 | Cut rerunStage tests (blocked), keep fan-in focus |
| StageDispatcherWiringTest | ~5 | Integration test |
| PipelineEndToEndTest | ~4 | Reduce scope, use configurable stub |
| **Total** | **~94** | Higher signal than original ~111 |

### Prerequisites

1. **Extract `CHAPTER_STAGES` and `BOOK_LEVEL_STAGES` to `StageKey`** — 10 min refactor, unblocks StageKeyTest and removes duplication between dispatcher and coordinator
2. **Add test constructor to `StageDispatcher`** — accepts `Map<StageKey, StageOperation>` + dependencies, bypasses annotation scanning
3. **Add test constructor or config injection to `IngestionPipelineCoordinator`** — for `@Value` fields (`staleTriggerGraceSeconds`, `staleRunningThresholdSeconds`, `maxStageAttempts`)
4. **Decide `rerunStage` test strategy** — defer until `deleteDataByStageId` is implemented, or test the orchestration sequence with stub no-op

### Open Questions

1. **StageKey classification refactor timing** — should we do this before writing tests, or write tests that work around the current private constants?
2. **rerunStage testing** — defer until `deleteDataByStageId` is implemented, or test the orchestration sequence with stub no-op?
3. **PipelineEndToEndTest mock strategy** — `@MockitoBean` vs `@TestConfiguration`? Configurable stub vs 16 stub classes?

---

## Test Suite Design (May 28, 2026)

Following the testing guidance in `docs/rules/developer-testing-workflow.md`, the test suite is organized by layer:

### Layer Classification

| Test Suite | Layer | Rationale |
|------------|-------|-----------|
| StageDagTest | **Business logic** | Pure data structure with algorithmic traversal (BFS, topological sort, connectivity validation) |
| StageKeyTest | **Business logic** | Enum classification methods encode pipeline semantics |
| StepResultTest | **Business logic** | Factory methods with validation logic |
| StageDispatcherTest | **Business logic** | Guard, idempotency, error boundary, MDC, completion emission |
| IngestionPipelineCoordinatorTest | **Business logic** | DAG barrier evaluation, bootstrap, recovery |
| StageDispatcherWiringTest | **Integration** | Spring context wiring |
| PipelineEndToEndTest | **Integration** | Full event loop across modules |

### Cuts (Testing Compiler)

The following were cut because they test record accessors and annotation metadata — no runtime behaviour:

- **DispatchContextTest** — record accessors
- **ForStageTest** — annotation metadata
- **StageOperationContractTest** — interface contract

Add tests only when runtime behaviour is added (validation, guards).

### Mock vs Fake vs Stub Strategy

| Test Suite | Strategy | Rationale |
|------------|----------|-----------|
| StageDispatcherTest | **Mock** | Verify interactions: `stageRepo.setRunningConditionally()`, `stageOutputRepo.existsByChapterIdAndStep()`, `eventPublisher.publishEvent()` |
| IngestionPipelineCoordinatorTest | **Mock** | Verify interactions: `stageRepo.tryTrigger()`, `eventPublisher.publishEvent()` |
| StageDispatcherWiringTest | **Stub** | Spring context needs beans, tests don't exercise them |
| PipelineEndToEndTest | **Stub** | Configurable stub handlers registered via `@TestConfiguration` |

### Revised Test Count

| Suite | Count | Notes |
|-------|-------|-------|
| StageDagTest | ~18 | Pure data structure, all justified |
| StageKeyTest | ~6 | Blocked on prerequisite (extract classification to enum) |
| StepResultTest | ~8 | Factory methods with validation |
| StageDispatcherTest | ~28 | Business logic with mocks |
| IngestionPipelineCoordinatorTest | ~25 | Cut rerunStage tests (blocked), keep fan-in focus |
| StageDispatcherWiringTest | ~5 | Integration test |
| PipelineEndToEndTest | ~4 | Reduce scope, use configurable stub |
| **Total** | **~94** | Higher signal than original ~111 |

### Prerequisites

1. **Extract `CHAPTER_STAGES` and `BOOK_LEVEL_STAGES` to `StageKey`** — 10 min refactor, unblocks StageKeyTest and removes duplication between dispatcher and coordinator
2. **Add test constructor to `StageDispatcher`** — accepts `Map<StageKey, StageOperation>` + dependencies, bypasses annotation scanning
3. **Add test constructor or config injection to `IngestionPipelineCoordinator`** — for `@Value` fields (`staleTriggerGraceSeconds`, `staleRunningThresholdSeconds`, `maxStageAttempts`)
4. **Decide `rerunStage` test strategy** — defer until `deleteDataByStageId` is implemented, or test the orchestration sequence with stub no-op

### Open Questions

1. **StageKey classification refactor timing** — should we do this before writing tests, or write tests that work around the current private constants?
2. **rerunStage testing** — defer until `deleteDataByStageId` is implemented, or test the orchestration sequence with stub no-op?
3. **PipelineEndToEndTest mock strategy** — `@MockitoBean` vs `@TestConfiguration`? Configurable stub vs 16 stub classes?
