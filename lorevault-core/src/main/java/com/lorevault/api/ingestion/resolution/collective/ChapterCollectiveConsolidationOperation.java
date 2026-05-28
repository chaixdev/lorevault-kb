package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for chapter-scoped collective resolution.
 *
 * <p>Implemented by {@link ChapterCollectiveConsolidationHandler} so that the step-by-step execution controller
 * can invoke collective resolution directly without going through Spring
 * {@code @TransactionalEventListener} dispatch.
 *
 * <p>The step-by-step execution controller provides the transaction context; this interface
 * simply exposes the business logic.
 */
public interface ChapterCollectiveConsolidationOperation extends StageOperation {

    /**
     * Execute collective resolution for a chapter within an existing transaction.
     *
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new DispatchContext(jobId, chapterId, null, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION));
    }
}
