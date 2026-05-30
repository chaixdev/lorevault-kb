package com.lorevault.api.orchestration.pipeline;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a single pipeline step execution.
 *
 * <p>Shared across all 13 ingestion steps because the structural differences
 * are limited to integer counts (scenes detected, chunks created, embeddings generated, etc.),
 * which are captured in the {@link #counts} map.
 *
 * @param success     whether the step completed without error
 * @param stepName    the pipeline stage key identifying this step
 * @param summary     human-readable summary of what happened
 * @param counts      step-specific integer metrics (e.g., "scenesDetected" → 5)
 * @param durationMs  wall-clock time in milliseconds
 * @param retryable   whether the failure is retryable (false for success results)
 */
public record StepResult(
        boolean success,
        StageKey stepName,
        String summary,
        Map<String, Integer> counts,
        long durationMs,
        boolean retryable
) {
    public StepResult {
        counts = counts != null ? Map.copyOf(counts) : Map.of();
    }

    /** Create a successful result with counts. */
    public static StepResult success(StageKey stepName, String summary, Map<String, Integer> counts, long durationMs) {
        return new StepResult(true, stepName, summary, counts, durationMs, false);
    }

    /** Create a successful result with no counts. */
    public static StepResult success(StageKey stepName, String summary, long durationMs) {
        return new StepResult(true, stepName, summary, Collections.emptyMap(), durationMs, false);
    }

    /** Create a failure result (non-retryable by default). */
    public static StepResult failure(StageKey stepName, String summary, long durationMs) {
        return new StepResult(false, stepName, summary, Collections.emptyMap(), durationMs, false);
    }

    /** Create a retryable failure result. */
    public static StepResult retryableFailure(StageKey stepName, String summary, long durationMs) {
        return new StepResult(false, stepName, summary, Collections.emptyMap(), durationMs, true);
    }
}