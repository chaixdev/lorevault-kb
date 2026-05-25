package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for book-scoped collective reduction.
 *
 * <p>Implemented by {@link BookCollectiveReductionHandler} so that the step-by-step execution controller
 * can invoke collective reduction directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
@FunctionalInterface
public interface BookCollectiveReductionOperation {

    /**
     * Execute collective reduction for a book within an existing transaction.
     *
     * @param jobId  the ingestion job ID (created by {@code prepare})
     * @param bookId the book to process
     * @return result summarising what happened
     */
    StepResult execute(UUID jobId, UUID bookId);
}
