package com.lorevault.api.orchestration.pipeline;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a single pipeline stage execution.
 *
 * <p>Shared across all ingestion stages because the structural differences
 * are limited to integer counts (scenes detected, chunks created, embeddings generated, etc.),
 * which are captured in the {@link #counts} map.
 *
 * @param success     whether the stage completed without error
 * @param stage       the pipeline stage key identifying this stage
 * @param summary     human-readable summary of what happened
 * @param counts      stage-specific integer metrics (e.g., "scenesDetected" → 5)
 * @param durationMs  wall-clock time in milliseconds
 * @param retryable   whether the failure is retryable (false for success results)
 */
public record StageResult(
        boolean success,
        StageKey stage,
        String summary,
        Map<String, Integer> counts,
        long durationMs,
        boolean retryable
) {
    public StageResult {
        counts = counts != null ? Map.copyOf(counts) : Map.of();
    }

    /** Create a successful result with counts. */
    public static StageResult success(StageKey stage, String summary, Map<String, Integer> counts, long durationMs) {
        return new StageResult(true, stage, summary, counts, durationMs, false);
    }

    /** Create a successful result with no counts. */
    public static StageResult success(StageKey stage, String summary, long durationMs) {
        return new StageResult(true, stage, summary, Collections.emptyMap(), durationMs, false);
    }

    /** Create a failure result (non-retryable by default). */
    public static StageResult failure(StageKey stage, String summary, long durationMs) {
        return new StageResult(false, stage, summary, Collections.emptyMap(), durationMs, false);
    }

    /** Create a retryable failure result. */
    public static StageResult retryableFailure(StageKey stage, String summary, long durationMs) {
        return new StageResult(false, stage, summary, Collections.emptyMap(), durationMs, true);
    }
}
