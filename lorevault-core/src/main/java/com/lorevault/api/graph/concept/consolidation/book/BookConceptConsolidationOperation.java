package com.lorevault.api.graph.concept.consolidation.book;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for book-scoped concept reduction.
 *
 * <p>Implemented by {@link BookConceptConsolidationHandler} so that the step-by-step execution controller
 * can invoke concept reduction directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
public interface BookConceptConsolidationOperation extends StageOperation {

    /**
     * Execute concept reduction for a book within an existing transaction.
     *
     * @param jobId  the ingestion job ID (created by {@code prepare})
     * @param bookId the book to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID bookId) {
        return execute(new StageExecutionContext(null, jobId, null, bookId, StageKey.BOOK_CONCEPT_CONSOLIDATION));
    }
}
