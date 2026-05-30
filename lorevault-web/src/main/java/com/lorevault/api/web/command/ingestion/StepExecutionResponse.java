package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.pipeline.StepKey;
import com.lorevault.api.orchestration.pipeline.StepResult;

import java.util.Map;

/**
 * Uniform response envelope for all step execution endpoints.
 *
 * <p>Maps from the core-layer {@link StepResult} and adds scope/scopeId
 * for client convenience. All step endpoints return this shape so that
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
public record StepExecutionResponse(
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
     * Create a response from a core-layer StepResult, adding scope context.
     *
     * <p>Uses {@link StepKey#toUrlSegment()} to convert the internal step name
     * to a kebab-case URL segment (e.g., "SCENE_DETECTION" → "detect-scenes").
     * This ensures the response step identifier matches the step catalog and
     * can be used to construct subsequent step URLs.
     */
    public static StepExecutionResponse from(StepResult result, StepKey stepKey, String scope, String scopeId) {
        return new StepExecutionResponse(
                stepKey.toUrlSegment(),
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