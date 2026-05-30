package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.signals.StageCompletedEvent;
import com.lorevault.api.orchestration.signals.StageTriggeredEvent;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StepKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Maps {@link StepKey} values to {@link StageKey} and publishes
 * {@link StageCompletedEvent}/{@link StageTriggeredEvent} when
 * {@code fireEvents=true} is set on a step execution request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StepEventMapper {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publishes a {@link StageCompletedEvent} for the given completed step.
     *
     * @param stepKey the step that just completed
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID that was processed
     * @param result  the outcome of the step execution
     */
    public void publishCompletionEvent(StepKey stepKey, UUID jobId, UUID scopeId, StepResult result) {
        StageCompletedEvent event = switch (stepKey) {
            // Chapter-level steps
            case DETECT_SCENES -> new StageCompletedEvent(this, jobId, scopeId, StageKey.SCENE_SEGMENTATION, result);
            case CHUNK -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHUNKING, result);
            case EMBED -> new StageCompletedEvent(this, jobId, scopeId, StageKey.EMBEDDING, result);
            case CHAPTER_CONSOLIDATE_INDIVIDUALS -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, result);
            case CHAPTER_CONSOLIDATE_COLLECTIVES -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION, result);
            case CHAPTER_CONSOLIDATE_LOCATIONS -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_LOCATION_CONSOLIDATION, result);
            case CHAPTER_CONSOLIDATE_OBJECTS -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_OBJECT_CONSOLIDATION, result);
            case CHAPTER_CONSOLIDATE_EVENTS -> new StageCompletedEvent(this, jobId, scopeId, StageKey.CHAPTER_EVENT_CONSOLIDATION, result);
            // Book-level steps (chapterId is null, scopeId is the bookId)
            case BOOK_CONSOLIDATE_INDIVIDUALS -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION, result);
            case BOOK_CONSOLIDATE_COLLECTIVES -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_COLLECTIVE_CONSOLIDATION, result);
            case BOOK_CONSOLIDATE_LOCATIONS -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_LOCATION_CONSOLIDATION, result);
            case BOOK_CONSOLIDATE_OBJECTS -> new StageCompletedEvent(this, jobId, null, scopeId, StageKey.BOOK_OBJECT_CONSOLIDATION, result);
        };
        eventPublisher.publishEvent(event);
        log.info("[StepEventMapper] Published StageCompletedEvent: stage={}, jobId={}, scopeId={}",
                event.getStage(), jobId, scopeId);
    }

    /**
     * Publishes a {@link StageTriggeredEvent} for the given step about to start.
     *
     * @param stepKey the step that is about to start
     * @param jobId   the ingestion job identifier
     * @param scopeId the chapter or book ID being processed
     */
    public void publishStartEvent(StepKey stepKey, UUID jobId, UUID scopeId) {
        StageTriggeredEvent event = switch (stepKey) {
            // Chapter-level steps
            case DETECT_SCENES -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.SCENE_SEGMENTATION);
            case CHUNK -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHUNKING);
            case EMBED -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.EMBEDDING);
            case CHAPTER_CONSOLIDATE_INDIVIDUALS -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
            case CHAPTER_CONSOLIDATE_COLLECTIVES -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION);
            case CHAPTER_CONSOLIDATE_LOCATIONS -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_LOCATION_CONSOLIDATION);
            case CHAPTER_CONSOLIDATE_OBJECTS -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_OBJECT_CONSOLIDATION);
            case CHAPTER_CONSOLIDATE_EVENTS -> new StageTriggeredEvent(this, jobId, scopeId, StageKey.CHAPTER_EVENT_CONSOLIDATION);
            // Book-level steps (chapterId is null, scopeId is the bookId)
            case BOOK_CONSOLIDATE_INDIVIDUALS -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_INDIVIDUAL_CONSOLIDATION);
            case BOOK_CONSOLIDATE_COLLECTIVES -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_COLLECTIVE_CONSOLIDATION);
            case BOOK_CONSOLIDATE_LOCATIONS -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_LOCATION_CONSOLIDATION);
            case BOOK_CONSOLIDATE_OBJECTS -> new StageTriggeredEvent(this, jobId, null, scopeId, StageKey.BOOK_OBJECT_CONSOLIDATION);
        };
        eventPublisher.publishEvent(event);
        log.info("[StepEventMapper] Published StageTriggeredEvent: stage={}, jobId={}, scopeId={}",
                event.getStage(), jobId, scopeId);
    }
}
