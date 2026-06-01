package com.lorevault.api.orchestration.scene;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

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
public interface SceneDetectionOperation extends StageOperation {

    /**
     * Execute scene detection for a chapter within an existing transaction.
     *
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new StageExecutionContext(null, jobId, chapterId, null, StageKey.SCENE_SEGMENTATION));
    }
}
