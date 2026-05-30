package com.lorevault.api.ai.embedding;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for embedding generation.
 *
 * <p>Implemented by {@link EmbeddingHandler} so that the step-by-step execution controller or
 * step-execution endpoints can invoke embedding generation directly
 * without going through Spring {@code @EventListener} dispatch.
 */
public interface EmbeddingOperation extends StageOperation {

    /**
     * Execute embedding generation for all chunks in a chapter.
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    default StepResult execute(UUID jobId, UUID chapterId) {
        return execute(new StageExecutionContext(null, jobId, chapterId, null, StageKey.EMBEDDING));
    }
}
