package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.coref.EventCoreferenceService;
import com.lorevault.api.ingestion.application.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.ingestion.domain.coref.EventCorefModels;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;

import java.util.List;
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
    private final ChapterGraphRepository chapterGraphRepository;
    private final PipelineStageSupport pipelineStageSupport;
    private final ApplicationEventPublisher eventPublisher;

    public ChapterEventResolutionHandler(
            EventCoreferenceService eventCoreferenceService,
            ChapterEventResolutionService chapterEventResolutionService,
            ChapterGraphRepository chapterGraphRepository,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.eventCoreferenceService = eventCoreferenceService;
        this.chapterEventResolutionService = chapterEventResolutionService;
        this.chapterGraphRepository = chapterGraphRepository;
        this.pipelineStageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.eventPublisher = eventPublisher;
    }

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        if (chapterGraphRepository.hasCompletedEventResolutionForJob(chapterId, jobId)) {
            log.warn(
                    "[CHAPTER_EVENT_RESOLUTION] Replay skipped: jobId={}, correlationId={}, chapterId={}, bookId={}",
                    jobId,
                    correlationId,
                    chapterId,
                    bookId
            );
            return;
        }

        log.info("[CHAPTER_EVENT_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);

        // Stage 2: LLM rolling-triad co-reference pass — writes SAME_EVENT links
        List<UUID> sceneIds = event.getSceneIds();
        EventCorefModels.CorefPassResult corefResult = pipelineStageSupport.runStage(
                this,
                STAGE_EVENT_COREF,
                jobId,
                chapterId,
                () -> eventCoreferenceService.runCorefPass(sceneIds, chapterId, jobId),
                e -> false
        );

        if (corefResult == null) {
            return;
        }

        log.info("[CHAPTER_EVENT_RESOLUTION] Stage 2 complete: jobId={}, correlationId={}, chapterId={}, windowsRun={}, linksCreated={}",
                jobId, correlationId, chapterId, corefResult.windowsRun(), corefResult.linksCreated());

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

        chapterGraphRepository.markEventResolutionCompleted(chapterId, jobId);

        eventPublisher.publishEvent(new ChapterEventsResolvedEvent(
                 this,
                 jobId,
                 correlationId,
                 chapterId,
                 bookId,
                 aggregationResult.success(),
                aggregationResult.rawMentionsProcessed(),
                aggregationResult.chapterEventsCreated()
        ));
    }
}
