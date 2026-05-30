package com.lorevault.api.graph.object.consolidation.chapter;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for chapter-scoped object resolution.
 *
 * <p>Implemented by {@link ChapterObjectConsolidationHandler} so that the step-by-step execution controller
 * can invoke object resolution directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
public interface ChapterObjectConsolidationOperation extends StageOperation {

    /**
     * Execute object resolution for a chapter within an existing transaction.
     *
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new StageExecutionContext(null, jobId, chapterId, null, StageKey.CHAPTER_OBJECT_CONSOLIDATION));
    }
}
