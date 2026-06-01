package com.lorevault.api.graph.individual.consolidation.chapter;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageOperation;

/**
 * Synchronous operation interface for chapter-scoped individual resolution.
 *
 * <p>Implemented by {@link ChapterIndividualConsolidationHandler} so that the step-by-step execution controller
 * can invoke individual resolution directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 *
 * <p>Callers must construct a {@link StageExecutionContext} with a valid {@code stageId}
 * when invoking {@link #execute(StageExecutionContext)}. The convenience method
 * {@code execute(UUID, UUID)} that defaulted to {@code stageId=null} has been removed
 * to ensure durable provenance on all created entities.
 */
public interface ChapterIndividualConsolidationOperation extends StageOperation {
}
