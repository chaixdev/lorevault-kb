package com.lorevault.api.ingestion.content;

import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for text chunking.
 *
 * <p>Implemented by {@link ChunkingHandler} so that the step-by-step execution controller or
 * step-execution endpoints can invoke chunking directly without going
 * through Spring {@code @EventListener} dispatch.
 */
public interface ChunkingOperation extends StageOperation {

    /**
     * Execute chunking for a chapter's scenes.
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new StageExecutionContext(null, jobId, chapterId, null, StageKey.CHUNKING));
    }
}
