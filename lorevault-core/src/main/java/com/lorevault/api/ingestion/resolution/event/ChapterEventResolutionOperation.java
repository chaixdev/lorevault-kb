package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for chapter-level event resolution.
 *
 * <p>Implemented by {@link ChapterEventResolutionHandler} so that the step-by-step execution controller
 * module or step-execution endpoints can invoke event resolution directly
 * without going through Spring {@code @EventListener} dispatch.
 *
 * <p>Executes Stage 2 (LLM co-reference pass) and Stage 3 (chapter event
 * aggregation) in sequence, returning a combined result.
 */
public interface ChapterEventResolutionOperation extends StageOperation {

    /**
     * Execute co-reference resolution and chapter event aggregation.
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new DispatchContext(jobId, chapterId, null, StageKey.CHAPTER_EVENT_RESOLUTION));
    }
}
