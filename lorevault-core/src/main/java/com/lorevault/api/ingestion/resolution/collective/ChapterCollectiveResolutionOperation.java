package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for chapter-scoped collective resolution.
 *
 * <p>Implemented by {@link ChapterCollectiveResolutionHandler} so that the CLI module
 * can invoke collective resolution directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The CLI module provides the transaction context; this interface
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
