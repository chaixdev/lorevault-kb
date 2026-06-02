package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.signals.StageCompletedEvent;
import com.lorevault.api.orchestration.signals.StageTriggeredEvent;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes {@link StageCompletedEvent}/{@link StageTriggeredEvent} when
 * {@code fireEvents=true} is set on a step execution request.
 *
 * <p>Accepts {@link StageKey} directly — the old {@code StepKey}→{@code StageKey}
 * mapping has been removed as part of the StepKey retirement.
 */
@Component
@Slf4j
public class StageEventMapper {

    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecutor ingestionTaskExecutor;

    public StageEventMapper(ApplicationEventPublisher eventPublisher,
                           @Qualifier("ingestionTaskExecutor") TaskExecutor ingestionTaskExecutor) {
        this.eventPublisher = eventPublisher;
        this.ingestionTaskExecutor = ingestionTaskExecutor;
    }

    /**
     * Publishes a {@link StageCompletedEvent} for the given completed stage.
     *
     * @param stage   the stage that just completed
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID that was processed
     * @param result  the outcome of the stage execution
     */
    public void publishCompletionEvent(StageKey stage, UUID jobId, UUID scopeId, StageResult result) {
        String correlationId = UUID.randomUUID().toString();
        StageCompletedEvent event = switch (stage) {
            // Chapter-level stages
            case SCENE_SEGMENTATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.SCENE_SEGMENTATION, result, correlationId);
            case CHUNKING -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHUNKING, result, correlationId);
            case EMBEDDING -> new StageCompletedEvent(this, jobId, scopeId, StageKey.EMBEDDING, result, correlationId);
            case CHAPTER_INDIVIDUAL_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, result, correlationId);
            case CHAPTER_COLLECTIVE_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION, result, correlationId);
            case CHAPTER_LOCATION_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_LOCATION_CONSOLIDATION, result, correlationId);
            case CHAPTER_OBJECT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_OBJECT_CONSOLIDATION, result, correlationId);
            case CHAPTER_EVENT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_EVENT_CONSOLIDATION, result, correlationId);
            case CHAPTER_CONCEPT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_CONCEPT_CONSOLIDATION, result, correlationId);
            // Book-level stages (chapterId is null, scopeId is the bookId)
            case BOOK_INDIVIDUAL_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION, result, correlationId);
            case BOOK_COLLECTIVE_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_COLLECTIVE_CONSOLIDATION, result, correlationId);
            case BOOK_LOCATION_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_LOCATION_CONSOLIDATION, result, correlationId);
            case BOOK_OBJECT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_OBJECT_CONSOLIDATION, result, correlationId);
            case BOOK_CONCEPT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_CONCEPT_CONSOLIDATION, result, correlationId);
            default -> throw new IllegalArgumentException("Unknown stage: " + stage);
        };
        CompletableFuture.runAsync(() -> eventPublisher.publishEvent(event), ingestionTaskExecutor)
                .exceptionally(ex -> {
                    log.error("[StageEventMapper] Failed to publish StageCompletedEvent: stage={}, jobId={}",
                            stage, jobId, ex);
                    return null;
                });
        log.info("[StageEventMapper] Dispatched StageCompletedEvent: stage={}, jobId={}, scopeId={}, correlationId={}",
                event.getStage(), jobId, scopeId, correlationId);
    }

    /**
     * Publishes a {@link StageTriggeredEvent} for the given stage about to start.
     *
     * @param stage   the stage that is about to start
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID being processed
     */
    public void publishStartEvent(StageKey stage, UUID jobId, UUID scopeId) {
        String correlationId = UUID.randomUUID().toString();
        StageTriggeredEvent event = switch (stage) {
            // Chapter-level stages
            case SCENE_SEGMENTATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.SCENE_SEGMENTATION, correlationId);
            case CHUNKING -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHUNKING, correlationId);
            case EMBEDDING -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.EMBEDDING, correlationId);
            case CHAPTER_INDIVIDUAL_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, correlationId);
            case CHAPTER_COLLECTIVE_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION, correlationId);
            case CHAPTER_LOCATION_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_LOCATION_CONSOLIDATION, correlationId);
            case CHAPTER_OBJECT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_OBJECT_CONSOLIDATION, correlationId);
            case CHAPTER_EVENT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_EVENT_CONSOLIDATION, correlationId);
            case CHAPTER_CONCEPT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_CONCEPT_CONSOLIDATION, correlationId);
            // Book-level stages (chapterId is null, scopeId is the bookId)
            case BOOK_INDIVIDUAL_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION, correlationId);
            case BOOK_COLLECTIVE_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_COLLECTIVE_CONSOLIDATION, correlationId);
            case BOOK_LOCATION_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_LOCATION_CONSOLIDATION, correlationId);
            case BOOK_OBJECT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_OBJECT_CONSOLIDATION, correlationId);
            case BOOK_CONCEPT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_CONCEPT_CONSOLIDATION, correlationId);
            default -> throw new IllegalArgumentException("Unknown stage: " + stage);
        };
        CompletableFuture.runAsync(() -> eventPublisher.publishEvent(event), ingestionTaskExecutor)
                .exceptionally(ex -> {
                    log.error("[StageEventMapper] Failed to publish StageTriggeredEvent: stage={}, jobId={}",
                            stage, jobId, ex);
                    return null;
                });
        log.info("[StageEventMapper] Dispatched StageTriggeredEvent: stage={}, jobId={}, scopeId={}, correlationId={}",
                event.getStage(), jobId, scopeId, correlationId);
    }
}
