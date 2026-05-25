package com.lorevault.api.ingestion.scene;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for scene detection.
 *
 * <p>Implemented by {@link SceneDetectionHandler} so that the step-by-step execution controller
 * can invoke scene detection directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
@FunctionalInterface
public interface SceneDetectionOperation {

    /**
     * Execute scene detection for a chapter within an existing transaction.
     *
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    StepResult execute(UUID jobId, UUID chapterId);
}