package com.lorevault.api.ingestion.content;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StepResult;

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
        return execute(new DispatchContext(jobId, chapterId, null, StageKey.EMBEDDING));
    }
}
