package com.lorevault.api.ingestion.pipeline;

import java.util.UUID;

/**
 * Execution context passed to {@link StageOperation#execute(DispatchContext)}.
 *
 * <p>Carries the IDs every handler needs. Chapter-level handlers use
 * {@link #jobId()} and {@link #chapterId()}; book-level handlers use
 * {@link #jobId()} and {@link #bookId()}. The {@link #stage()} field
 * is available for handlers that need to know which stage is executing
 * (e.g. for constructing {@link StepResult}).
 *
 * @param jobId     the ingestion job ID (never null)
 * @param chapterId the chapter being processed (never null for chapter-level stages)
 * @param bookId    the book being processed (nullable; non-null for book-level stages)
 * @param stage     the pipeline stage key
 */
public record DispatchContext(
        UUID jobId,
        UUID chapterId,
        UUID bookId,
        StageKey stage
) {}
