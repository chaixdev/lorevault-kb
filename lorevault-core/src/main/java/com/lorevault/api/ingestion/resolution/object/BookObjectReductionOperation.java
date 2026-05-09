package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for book-scoped object reduction.
 *
 * <p>Implemented by {@link BookObjectReductionHandler} so that the CLI module
 * can invoke object reduction directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The CLI module provides the transaction context; this interface
 * simply exposes the business logic.
 */
@FunctionalInterface
public interface BookObjectReductionOperation {

    /**
     * Execute object reduction for a book within an existing transaction.
     *
     * @param jobId  the ingestion job ID (created by {@code prepare})
     * @param bookId the book to process
     * @return result summarising what happened
     */
    StepResult execute(UUID jobId, UUID bookId);
}
