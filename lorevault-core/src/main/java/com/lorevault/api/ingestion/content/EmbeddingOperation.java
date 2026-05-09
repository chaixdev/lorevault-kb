package com.lorevault.api.ingestion.content;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for embedding generation.
 *
 * <p>Implemented by {@link EmbeddingHandler} so that the CLI module or
 * step-execution endpoints can invoke embedding generation directly
 * without going through Spring {@code @EventListener} dispatch.
 */
@FunctionalInterface
public interface EmbeddingOperation {

    /**
     * Execute embedding generation for all chunks in a chapter.
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    StepResult execute(UUID jobId, UUID chapterId);
}
