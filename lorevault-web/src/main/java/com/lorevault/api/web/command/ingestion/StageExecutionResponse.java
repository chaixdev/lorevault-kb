package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;

import java.util.Map;

/**
 * Uniform response envelope for all stage execution endpoints.
 *
 * <p>Maps from the core-layer {@link StageResult} and adds scope/scopeId
 * for client convenience. All stage endpoints return this shape so that
 * agents can parse responses consistently.
 *
 * @param step       kebab-case step identifier (e.g. "detect-scenes")
 * @param scope      "chapter" or "book"
 * @param scopeId    the chapter or book ID that was processed
 * @param success    whether the step completed without error
 * @param summary    human-readable summary of what happened
 * @param durationMs wall-clock time in milliseconds
 * @param retryable  whether a failure is retryable (false for successes)
 * @param counts     step-specific integer metrics
 */
public record StageExecutionResponse(
        String step,
        String scope,
        String scopeId,
        boolean success,
        String summary,
        long durationMs,
        boolean retryable,
        Map<String, Integer> counts
) {
    /**
     * Create a response from a core-layer StageResult, adding scope context.
     *
     * <p>Uses {@link StageKey#toUrlSegment()} to convert the internal stage name
     * to a kebab-case URL segment (e.g., "SCENE_SEGMENTATION" → "scene-segmentation").
     * This ensures the response step identifier matches the step catalog and
     * can be used to construct subsequent step URLs.
     */
    public static StageExecutionResponse from(StageResult result, StageKey stageKey, String scope, String scopeId) {
        return new StageExecutionResponse(
                stageKey.toUrlSegment(),
                scope,
                scopeId,
                result.success(),
                result.summary(),
                result.durationMs(),
                result.retryable(),
                result.counts()
        );
    }
}
