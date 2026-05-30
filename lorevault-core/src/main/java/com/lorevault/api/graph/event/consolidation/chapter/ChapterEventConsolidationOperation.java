package com.lorevault.api.graph.event.consolidation.chapter;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for chapter-level event resolution.
 *
 * <p>Implemented by {@link ChapterEventConsolidationHandler} so that the step-by-step execution controller
 * module or step-execution endpoints can invoke event resolution directly
 * without going through Spring {@code @EventListener} dispatch.
 *
 * <p>Executes Stage 2 (LLM co-reference pass) and Stage 3 (chapter event
 * aggregation) in sequence, returning a combined result.
 */
public interface ChapterEventConsolidationOperation extends StageOperation {

    /**
     * Execute co-reference resolution and chapter event aggregation.
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new StageExecutionContext(null, jobId, chapterId, null, StageKey.CHAPTER_EVENT_CONSOLIDATION));
    }
}
