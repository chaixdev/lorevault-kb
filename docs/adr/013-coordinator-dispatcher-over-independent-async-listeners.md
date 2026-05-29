# ADR 013: Coordinator/Dispatcher Over Independent Async Listeners

**Status:** Accepted
**Date:** May 2026
**Supersedes:** ADR 004 (partially — the pipeline remains event-driven, but the dispatch mechanism changes)

## Context

LoreVault's ingestion pipeline had 15 handler methods, each annotated with `@Async @EventListener`, independently listening for `StageTriggeredEvent`. Each handler set its own MDC, managed its own error boundary, and emitted its own `StageCompletedEvent`.

This created several problems:

- **No single point of control.** Executor routing, MDC setup, idempotency checks, and error handling were duplicated across every handler.
- **No startup validation.** A missing `@ForStage` annotation caused silent failures at runtime rather than a fast failure at startup.
- **No unified observability.** Each handler independently set MDC fields, making it easy to miss one or set them inconsistently.
- **Hard to add cross-cutting behavior.** Adding provenance tagging, timing, or retry policies meant touching every handler.

## Decision

Replace the 15 independent `@Async @EventListener` methods with a single `StageDispatcher` that:

1. **Receives all `StageTriggeredEvent`s** through one `@EventListener` method
2. **Routes to the correct executor** — `sceneDetectionTaskExecutor` for `SCENE_SEGMENTATION`, `ingestionLaneTaskExecutor` for everything else
3. **Sets MDC context** (`stage`, `jobId`, `stageId`) before execution and clears it after
4. **Performs an atomic `TRIGGERED→RUNNING` transition** as a guard against duplicate execution
5. **Checks idempotency** — skips already-completed stages
6. **Wraps execution in an error boundary** that converts unhandled exceptions to `StepResult.failure`
7. **Emits `StageCompletedEvent`** on completion, regardless of success or failure

Each handler implements `StageOperation` with a `@ForStage(StageKey)` annotation. The dispatcher validates at startup that every `StageKey` has exactly one handler.

## Alternatives Considered

### Keep independent `@Async @EventListener` methods

The original pattern. Rejected because:
- Cross-cutting concerns (MDC, error boundary, idempotency) are duplicated across 15 handlers
- No startup validation — missing handlers fail silently at runtime
- Adding new cross-cutting behavior requires touching every handler

### Spring Integration scatter-gather

Use Spring Integration's channel architecture to route events. Rejected because:
- Adds a heavy framework dependency for a problem that a single class solves
- The dispatcher is already event-driven; Spring Integration adds ceremony without benefit
- Harder to debug and trace than a plain `@EventListener`

### ApplicationEventPublisher with interceptor chain

Keep `@EventListener` but add a `HandlerInterceptor`-style chain for cross-cutting concerns. Rejected because:
- Spring's event listener infrastructure doesn't natively support interceptor chains
- Would require custom infrastructure that duplicates what the dispatcher already does
- Still lacks startup validation

## Implications

- ADR 004's decision to keep the event-driven pipeline stands. The pipeline is still event-driven — `StageTriggeredEvent` triggers `StageDispatcher`, which delegates to `StageOperation.execute(ctx)`. The dispatch mechanism changed, not the event-driven nature.
- All new stages must implement `StageOperation` and register with `@ForStage`. The dispatcher's startup validation will fail fast if a `StageKey` has no handler or duplicate handlers.
- The dispatcher is **not** `@Transactional`. Each handler manages its own transaction boundaries. Holding a dispatcher-level transaction across LLM calls (30–120s) would exhaust the Neo4j connection pool.
- Executor routing is explicit: `SCENE_SEGMENTATION` uses a dedicated executor; all other stages share `ingestionLaneTaskExecutor`.