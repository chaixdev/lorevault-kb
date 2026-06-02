package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.signals.StageCompletedEvent;
import com.lorevault.api.orchestration.signals.StageTriggeredEvent;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes {@link StageCompletedEvent}/{@link StageTriggeredEvent} when
 * {@code fireEvents=true} is set on a step execution request.
 *
 * <p>Accepts {@link StageKey} directly — the old {@code StepKey}→{@code StageKey}
 * mapping has been removed as part of the StepKey retirement.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StepEventMapper {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publishes a {@link StageCompletedEvent} for the given completed stage.
     *
     * @param stage   the stage that just completed
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID that was processed
     * @param result  the outcome of the stage execution
     */
    public void publishCompletionEvent(StageKey stage, UUID jobId, UUID scopeId, StageResult result) {
        StageCompletedEvent event = switch (stage) {
            // Chapter-level stages
            case SCENE_SEGMENTATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.SCENE_SEGMENTATION, result);
            case CHUNKING -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHUNKING, result);
            case EMBEDDING -> new StageCompletedEvent(this, jobId, scopeId, StageKey.EMBEDDING, result);
            case CHAPTER_INDIVIDUAL_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, result);
            case CHAPTER_COLLECTIVE_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION, result);
            case CHAPTER_LOCATION_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_LOCATION_CONSOLIDATION, result);
            case CHAPTER_OBJECT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_OBJECT_CONSOLIDATION, result);
            case CHAPTER_EVENT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_EVENT_CONSOLIDATION, result);
            case CHAPTER_CONCEPT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_CONCEPT_CONSOLIDATION, result);
            // Book-level stages (chapterId is null, scopeId is the bookId)
            case BOOK_INDIVIDUAL_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION, result);
            case BOOK_COLLECTIVE_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_COLLECTIVE_CONSOLIDATION, result);
            case BOOK_LOCATION_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_LOCATION_CONSOLIDATION, result);
            case BOOK_OBJECT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_OBJECT_CONSOLIDATION, result);
            case BOOK_CONCEPT_CONSOLIDATION -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_CONCEPT_CONSOLIDATION, result);
            default -> throw new IllegalArgumentException("Unknown stage: " + stage);
        };
        eventPublisher.publishEvent(event);
        log.info("[StepEventMapper] Published StageCompletedEvent: stage={}, jobId={}, scopeId={}",
                event.getStage(), jobId, scopeId);
    }

    /**
     * Publishes a {@link StageTriggeredEvent} for the given stage about to start.
     *
     * @param stage   the stage that is about to start
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID being processed
     */
    public void publishStartEvent(StageKey stage, UUID jobId, UUID scopeId) {
        StageTriggeredEvent event = switch (stage) {
            // Chapter-level stages
            case SCENE_SEGMENTATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.SCENE_SEGMENTATION);
            case CHUNKING -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHUNKING);
            case EMBEDDING -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.EMBEDDING);
            case CHAPTER_INDIVIDUAL_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
            case CHAPTER_COLLECTIVE_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION);
            case CHAPTER_LOCATION_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_LOCATION_CONSOLIDATION);
            case CHAPTER_OBJECT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_OBJECT_CONSOLIDATION);
            case CHAPTER_EVENT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_EVENT_CONSOLIDATION);
            case CHAPTER_CONCEPT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_CONCEPT_CONSOLIDATION);
            // Book-level stages (chapterId is null, scopeId is the bookId)
            case BOOK_INDIVIDUAL_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION);
            case BOOK_COLLECTIVE_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_COLLECTIVE_CONSOLIDATION);
            case BOOK_LOCATION_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_LOCATION_CONSOLIDATION);
            case BOOK_OBJECT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_OBJECT_CONSOLIDATION);
            case BOOK_CONCEPT_CONSOLIDATION -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_CONCEPT_CONSOLIDATION);
            default -> throw new IllegalArgumentException("Unknown stage: " + stage);
        };
        eventPublisher.publishEvent(event);
        log.info("[StepEventMapper] Published StageTriggeredEvent: stage={}, jobId={}, scopeId={}",
                event.getStage(), jobId, scopeId);
    }
}
