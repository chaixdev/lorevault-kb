package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for book-scoped location reduction.
 *
 * <p>Implemented by {@link BookLocationReductionHandler} so that the step-by-step execution controller
 * can invoke location reduction directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
public interface BookLocationReductionOperation extends StageOperation {

    /**
     * Execute location reduction for a book within an existing transaction.
     *
     * @param jobId  the ingestion job ID (created by {@code prepare})
     * @param bookId the book to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID bookId) {
        return execute(new DispatchContext(jobId, null, bookId, StageKey.BOOK_LOCATION_REDUCTION));
    }
}
