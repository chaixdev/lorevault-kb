package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.coref.EventCoreferenceService;
import com.lorevault.api.ingestion.application.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;
import com.lorevault.api.ingestion.domain.coref.EventCorefModels;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async handler that orchestrates Stage 2 (LLM co-reference) and Stage 3 (ChapterEvent aggregation)
 * in response to a {@link ScenesDetectedEvent}.
 *
 * <p>Uses {@link PipelineStageSupport} for durable failure emission — any exception from either
 * stage emits {@code IngestionFailedEvent} and marks the job FAILED, rather than silently swallowing
 * or rethrowing to the executor.</p>
 *
 * <p>On success, publishes {@link ChapterEventsResolvedEvent} so the
 * {@code IngestionCompletionCoordinator} fan-in can unblock.</p>
 */
@Component
@Slf4j
public class ChapterEventResolutionHandler {

    static final String STAGE_EVENT_COREF = "EVENT_COREF";
    static final String STAGE_CHAPTER_EVENT_AGGREGATION = "CHAPTER_EVENT_AGGREGATION";

    private final EventCoreferenceService eventCoreferenceService;
    private final ChapterEventResolutionService chapterEventResolutionService;
    private final PipelineStageSupport pipelineStageSupport;
    private final ApplicationEventPublisher eventPublisher;

    public ChapterEventResolutionHandler(
            EventCoreferenceService eventCoreferenceService,
            ChapterEventResolutionService chapterEventResolutionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.eventCoreferenceService = eventCoreferenceService;
        this.chapterEventResolutionService = chapterEventResolutionService;
        this.pipelineStageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.eventPublisher = eventPublisher;
    }

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID bookId = event.getBookId();

        log.info("[CHAPTER_EVENT_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        // Stage 2: LLM rolling-triad co-reference pass — writes SAME_EVENT links
        EventCorefModels.CorefPassResult corefResult = pipelineStageSupport.runStage(
                this,
                STAGE_EVENT_COREF,
                jobId,
                chapterId,
                () -> eventCoreferenceService.runCorefPass(chapterId, jobId),
                e -> false
        );

        if (corefResult == null) {
            // runStage already emitted IngestionFailedEvent and marked job FAILED
            log.warn("[CHAPTER_EVENT_RESOLUTION] Stage 2 failed, aborting Stage 3: jobId={}, chapterId={}", jobId, chapterId);
            return;
        }

        log.info("[CHAPTER_EVENT_RESOLUTION] Stage 2 complete: jobId={}, chapterId={}, windowsRun={}, linksCreated={}",
                jobId, chapterId, corefResult.windowsRun(), corefResult.linksCreated());

        // Stage 3: deterministic aggregation from SAME_EVENT chains → ChapterEvent nodes
        ChapterEventResolutionResult aggregationResult = pipelineStageSupport.runStage(
                this,
                STAGE_CHAPTER_EVENT_AGGREGATION,
                jobId,
                chapterId,
                () -> chapterEventResolutionService.resolveChapter(chapterId),
                e -> false
        );

        if (aggregationResult == null) {
            // runStage already emitted IngestionFailedEvent and marked job FAILED
            log.warn("[CHAPTER_EVENT_RESOLUTION] Stage 3 failed: jobId={}, chapterId={}", jobId, chapterId);
            return;
        }

        if (aggregationResult.success()) {
            log.info(
                    "[CHAPTER_EVENT_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterEventCount={}",
                    jobId, chapterId, bookId,
                    aggregationResult.rawMentionsProcessed(),
                    aggregationResult.chapterEventsCreated()
            );
        } else {
            log.warn(
                    "[CHAPTER_EVENT_RESOLUTION] Skipped (no mentions): jobId={}, chapterId={}, bookId={}, reason={}",
                    jobId, chapterId, bookId, aggregationResult.message()
            );
        }

        eventPublisher.publishEvent(new ChapterEventsResolvedEvent(
                this,
                jobId,
                chapterId,
                bookId,
                aggregationResult.success(),
                aggregationResult.rawMentionsProcessed(),
                aggregationResult.chapterEventsCreated()
        ));
    }
}
