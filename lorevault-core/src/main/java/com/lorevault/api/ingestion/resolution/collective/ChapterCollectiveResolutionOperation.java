package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for chapter-scoped collective resolution.
 *
 * <p>Implemented by {@link ChapterCollectiveResolutionHandler} so that the step-by-step execution controller
 * can invoke collective resolution directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
@FunctionalInterface
public interface ChapterCollectiveResolutionOperation {

    /**
     * Execute collective resolution for a chapter within an existing transaction.
     *
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    StepResult execute(UUID jobId, UUID chapterId);
}
