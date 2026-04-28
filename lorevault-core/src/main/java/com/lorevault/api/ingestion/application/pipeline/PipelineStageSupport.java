package com.lorevault.api.ingestion.application.pipeline;

import com.lorevault.api.ai.domain.EmbeddingFailure;
import com.lorevault.api.ai.domain.EmbeddingGenerationException;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Small helper to reduce duplicated handler boilerplate:
 * - consistent failure event emission
 * - consistent job FAILED updates
 * - optional status updates with default empty properties
 */
@Slf4j
public class PipelineStageSupport {

    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;

    public PipelineStageSupport(IngestionJobService ingestionJobService, ApplicationEventPublisher eventPublisher) {
        this.ingestionJobService = ingestionJobService;
        this.eventPublisher = eventPublisher;
    }

    public void updateJobStatus(UUID jobId, IngestionStatus status, String description) {
        updateJobStatus(jobId, status, description, Collections.emptyMap());
    }

    public void updateJobStatus(UUID jobId, IngestionStatus status, String description, Map<String, Object> properties) {
        ingestionJobService.updateJobStatus(jobId, status, description, properties == null ? Collections.emptyMap() : properties);
    }

    /**
     * Execute a stage of the pipeline. On exception, emits {@link IngestionFailedEvent} and updates job status to FAILED.
     *
     * @param stage logical stage name (e.g., SCENE_DETECTION)
     * @param jobId job id
     * @param chapterId chapter id
     * @param work the work to execute
     * @param isRetryable function to determine whether the error is retryable
     */
    public <T> T runStage(
            Object source,
            String stage,
            UUID jobId,
            UUID chapterId,
            Supplier<T> work,
            Function<Exception, Boolean> isRetryable
    ) {
        return runStage(source, stage, jobId, jobId, chapterId, work, isRetryable);
    }

    /**
     * Execute a stage with an explicit event correlation id preserved on emitted failure events.
     */
    public <T> T runStage(
            Object source,
            String stage,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            Supplier<T> work,
            Function<Exception, Boolean> isRetryable
    ) {
        try {
            return work.get();
        } catch (Exception e) {
            boolean retryable = false;
            try {
                retryable = isRetryable != null && Boolean.TRUE.equals(isRetryable.apply(e));
            } catch (Exception classifierError) {
                log.debug("Failure classifier threw for stage {} job={} correlationId={} chapter={}: {}",
                        stage, jobId, correlationId, chapterId, classifierError.getMessage());
            }

            if (retryable) {
                log.warn("Stage {} failed for job={} correlationId={} chapter={}: {}", stage, jobId, correlationId, chapterId, safeMessage(e));
                log.debug("Stage {} retryable failure details for job={} correlationId={} chapter={}", stage, jobId, correlationId, chapterId, e);
            } else {
                log.error("Stage {} failed for job={} correlationId={} chapter={}: {}", stage, jobId, correlationId, chapterId, safeMessage(e));
                log.debug("Stage {} failure details for job={} correlationId={} chapter={}", stage, jobId, correlationId, chapterId, e);
            }

            eventPublisher.publishEvent(new IngestionFailedEvent(
                    source != null ? source : this, jobId, correlationId, chapterId, stage, safeMessage(e), retryable));

            IngestionFailure failure = extractFailure(stage, e);

            ingestionJobService.updateJobStatus(
                    jobId,
                    IngestionStatus.FAILED,
                    stage + " failed: " + safeMessage(e),
                    failure.toProperties());

            // Preserve prior handler behavior: swallow exceptions after emitting failure + FAILED status.
            return null;
        }
    }

    private String safeMessage(Exception e) {
        return sanitizeExceptionMessage(e);
    }

    /**
     * Produces a log-safe, single-line representation of an exception message.
     * <p>
     * Strips CR, LF, and ASCII control characters to prevent log-injection, then
     * truncates to 200 characters. Falls back to the simple class name when the
     * message is null or blank after sanitization.
     * </p>
     *
     * @param e the exception whose message is being sanitized
     * @return a sanitized, truncated string safe for logs and status fields
     */
    public static String sanitizeExceptionMessage(Exception e) {
        if (e == null) {
            return "unknown error";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        // Strip CR, LF and other ASCII control characters (< 0x20, except space)
        String sanitized = message.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", " ").strip();
        if (sanitized.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "…" : sanitized;
    }

    private IngestionFailure extractFailure(String stage, Exception e) {
        if (e instanceof EmbeddingGenerationException embeddingGenerationException
                && embeddingGenerationException.failure() != null) {
            return toIngestionFailure(embeddingGenerationException.failure());
        }
        if (e instanceof com.lorevault.api.ingestion.domain.IngestionFailureCarrier carrier
                && carrier.failure() != null) {
            return carrier.failure();
        }
        return IngestionFailure.fromException(stage, e);
    }

    private IngestionFailure toIngestionFailure(EmbeddingFailure failure) {
        IngestionFailure.Builder builder = IngestionFailure.builder(failure.code(), failure.message())
                .exceptionType(failure.exceptionType())
                .stage(failure.stage());
        if (failure.details() != null) {
            failure.details().forEach(builder::detail);
        }
        return builder.build();
    }
}
