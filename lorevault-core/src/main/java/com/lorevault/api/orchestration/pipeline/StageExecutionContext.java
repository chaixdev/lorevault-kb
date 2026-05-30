package com.lorevault.api.orchestration.pipeline;

import java.util.UUID;

/**
 * Execution identity for a single pipeline stage invocation.
 *
 * <p>Flows from {@code StageDispatcher} through the handler → service →
 * repository chain into domain data. Every node or relationship created
 * during this execution carries {@link #stageId()} as a provenance property.
 *
 * <p>Feeds three observability tiers:
 * <ul>
 *   <li><b>Logging:</b> MDC ({@code stage}, {@code jobId}, {@code chapterId})</li>
 *   <li><b>Graph audit:</b> {@code stageId} tagged on domain nodes/edges</li>
 *   <li><b>Metrics:</b> Micrometer Timer tags (planned)</li>
 * </ul>
 *
 * @param stageId   the Stage node ID — the durable execution identity
 * @param jobId     the ingestion job ID (never null)
 * @param chapterId the chapter being processed (nullable for book-level stages)
 * @param bookId    the book being processed (nullable for chapter-level stages)
 * @param stage     the pipeline stage key
 */
public record StageExecutionContext(
        UUID stageId,
        UUID jobId,
        UUID chapterId,
        UUID bookId,
        StageKey stage
) {}
