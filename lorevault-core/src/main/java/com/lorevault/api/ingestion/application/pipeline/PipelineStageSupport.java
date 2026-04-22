package com.lorevault.api.ingestion.application.pipeline;

import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

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
        try {
            return work.get();
        } catch (Exception e) {
            boolean retryable = false;
            try {
                retryable = isRetryable != null && Boolean.TRUE.equals(isRetryable.apply(e));
            } catch (Exception classifierError) {
                log.debug("Failure classifier threw for stage {} job={} chapter={}: {}",
                        stage, jobId, chapterId, classifierError.getMessage());
            }

            if (retryable) {
                log.warn("Stage {} failed for job={} chapter={}: {}", stage, jobId, chapterId, safeMessage(e));
                log.debug("Stage {} retryable failure details for job={} chapter={}", stage, jobId, chapterId, e);
            } else {
                log.error("Stage {} failed for job={} chapter={}: {}", stage, jobId, chapterId, safeMessage(e));
                log.debug("Stage {} failure details for job={} chapter={}", stage, jobId, chapterId, e);
            }

            eventPublisher.publishEvent(new IngestionFailedEvent(
                source != null ? source : this, jobId, chapterId, stage, safeMessage(e), retryable));

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
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private IngestionFailure extractFailure(String stage, Exception e) {
        if (e instanceof com.lorevault.api.ai.domain.TriadAnalysisException triadAnalysisException
                && triadAnalysisException.failure() != null) {
            return triadAnalysisException.failure();
        }
        if (e instanceof com.lorevault.api.ai.domain.SceneLocalizationException sceneLocalizationException
                && sceneLocalizationException.failure() != null) {
            return sceneLocalizationException.failure();
        }
        return IngestionFailure.fromException(stage, e);
    }
}
