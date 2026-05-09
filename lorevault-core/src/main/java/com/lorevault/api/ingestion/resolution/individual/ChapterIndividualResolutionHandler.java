package com.lorevault.api.ingestion.resolution.individual;

import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChapterIndividualResolutionHandler implements ChapterIndividualResolutionOperation {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChapterIndividualResolutionHandler(
            ChapterIndividualResolutionService chapterIndividualResolutionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        log.info("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        StepResult result = execute(jobId, chapterId);

        if (result.success()) {
            eventPublisher.publishEvent(new ChapterIndividualsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("rawIndividualsProcessed", 0),
                    result.counts().getOrDefault("chapterIndividualsCreated", 0)
            ));
        } else {
            log.error("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    "CHAPTER_INDIVIDUAL_RESOLUTION", result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    "CHAPTER_INDIVIDUAL_RESOLUTION failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        stageSupport.updateJobStatus(jobId, IngestionStatus.RESOLVING_INDIVIDUALS, "Resolving individual entities for chapter...");

        try {
            ChapterIndividualResolutionResult response = chapterIndividualResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated()
                );
            } else {
                log.warn(
                        "[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success("CHAPTER_INDIVIDUAL_RESOLUTION",
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawIndividualsProcessed", response.rawIndividualsProcessed(),
                            "chapterIndividualsCreated", response.chapterIndividualsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure("CHAPTER_INDIVIDUAL_RESOLUTION",
                    PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        }
    }
}
