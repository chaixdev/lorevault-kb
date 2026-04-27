package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.coref.EventCoreferenceService;
import com.lorevault.api.ingestion.application.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;
import com.lorevault.api.ingestion.domain.IngestionStatus;
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
    static final String STAGE_EVENT_RESOLUTION_PUBLISH = "EVENT_RESOLUTION_PUBLISH";

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
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        log.info("[CHAPTER_EVENT_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);
        pipelineStageSupport.updateJobStatus(
                jobId,
                IngestionStatus.EVENT_COREF,
                "Resolving cross-scene event co-reference",
                java.util.Map.of(
                        "correlationId", correlationId.toString(),
                        "chapterId", chapterId.toString(),
                        "sceneCount", sceneIdsOrEmpty(event.getSceneIds()).size()
                )
        );

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

        pipelineStageSupport.updateJobStatus(
                jobId,
                IngestionStatus.CHAPTER_EVENT_AGGREGATION,
                "Aggregating chapter-level events from co-reference chains",
                java.util.Map.of(
                        "correlationId", correlationId.toString(),
                        "chapterId", chapterId.toString(),
                        "windowsRun", corefResult.windowsRun(),
                        "linksCreated", corefResult.linksCreated(),
                        "failedCorefWindowCount", corefResult.failedWindowCount()
                )
        );

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

        aggregationResult = new ChapterEventResolutionResult(
                aggregationResult.chapterId(),
                aggregationResult.success(),
                aggregationResult.rawMentionsProcessed(),
                aggregationResult.chapterEventsCreated(),
                corefResult.failedWindowCount(),
                aggregationResult.message()
        );

        if (aggregationResult.success()) {
            log.info(
                    "[CHAPTER_EVENT_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterEventCount={}, failedCorefWindowCount={}",
                    jobId, chapterId, bookId,
                    aggregationResult.rawMentionsProcessed(),
                    aggregationResult.chapterEventsCreated(),
                    aggregationResult.failedCorefWindowCount()
            );
        } else {
            log.warn(
                    "[CHAPTER_EVENT_RESOLUTION] Skipped (no mentions): jobId={}, chapterId={}, bookId={}, failedCorefWindowCount={}, reason={}",
                    jobId, chapterId, bookId, aggregationResult.failedCorefWindowCount(), aggregationResult.message()
            );
        }

        ChapterEventResolutionResult finalResult = aggregationResult;
        pipelineStageSupport.runStage(
                this,
                STAGE_EVENT_RESOLUTION_PUBLISH,
                jobId,
                chapterId,
                () -> {
                    eventPublisher.publishEvent(new ChapterEventsResolvedEvent(
                            this,
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            finalResult.success(),
                            finalResult.rawMentionsProcessed(),
                            finalResult.chapterEventsCreated(),
                            finalResult.failedCorefWindowCount()
                    ));
                    return null;
                },
                e -> false
        );
    }

    private List<UUID> sceneIdsOrEmpty(List<UUID> sceneIds) {
        return sceneIds == null ? List.of() : sceneIds;
    }
}
