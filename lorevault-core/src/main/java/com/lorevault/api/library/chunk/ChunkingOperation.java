package com.lorevault.api.library.chunk;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

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
