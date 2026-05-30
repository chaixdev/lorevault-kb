package com.lorevault.api.graph.individual.consolidation.book;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for book-scoped individual reduction.
 *
 * <p>Implemented by {@link BookIndividualConsolidationHandler} so that the step-by-step execution controller
 * can invoke individual reduction directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
public interface BookIndividualConsolidationOperation extends StageOperation {

    /**
     * Execute individual reduction for a book within an existing transaction.
     *
     * @param jobId  the ingestion job ID (created by {@code prepare})
     * @param bookId the book to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID bookId) {
        return execute(new StageExecutionContext(null, jobId, null, bookId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION));
    }
}
