package com.lorevault.api.ingestion.pipeline;

/**
 * Functional contract for a single pipeline stage handler.
 *
 * <p>Replaces the {@code @Async @EventListener onTrigger} boilerplate that
 * was duplicated across 15 handlers. The {@code StageDispatcher} invokes
 * {@link #execute(StageExecutionContext)} after handling guard, idempotency, and
 * error-boundary concerns. Handlers contain pure domain logic — no
 * orchestration fields, no Spring event annotations.
 *
 * <p>Callers that need step-by-step execution (e.g.
 * {@code StepExecutionCommandController}) should use the lane-specific
 * {@code *Operation} subinterfaces, which extend this interface and provide
 * a backward-compatible {@code execute(UUID, UUID)} default method.
 *
 * @see StageKey
 * @see StageDispatcher
 * @see StageExecutionContext
 */
@FunctionalInterface
public interface StageOperation {

    /**
     * Execute the stage's domain logic.
     *
     * @param ctx execution context carrying stageId, jobId, chapterId, bookId, and stage
     * @return result summarising success/failure, counts, and elapsed time
     */
    StepResult execute(StageExecutionContext ctx);
}
