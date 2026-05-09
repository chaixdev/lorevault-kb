package com.lorevault.api.ingestion.content;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.UUID;

/**
 * Synchronous operation interface for text chunking.
 *
 * <p>Implemented by {@link ChunkingHandler} so that the CLI module or
 * step-execution endpoints can invoke chunking directly without going
 * through Spring {@code @EventListener} dispatch.
 */
@FunctionalInterface
public interface ChunkingOperation {

    /**
     * Execute chunking for a chapter's scenes.
     *
     * @param jobId     the ingestion job ID
     * @param chapterId the chapter to process
     * @return result summarising what happened
     */
    StepResult execute(UUID jobId, UUID chapterId);
}
