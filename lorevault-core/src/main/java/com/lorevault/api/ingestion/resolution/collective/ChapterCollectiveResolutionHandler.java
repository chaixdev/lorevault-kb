package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChapterCollectiveResolutionHandler implements ChapterCollectiveResolutionOperation {

    static final String STAGE_CHAPTER_COLLECTIVE_RESOLUTION = "CHAPTER_COLLECTIVE_RESOLUTION";

    private final ChapterCollectiveResolutionService chapterCollectiveResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChapterCollectiveResolutionHandler(
            ChapterCollectiveResolutionService chapterCollectiveResolutionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterCollectiveResolutionService = chapterCollectiveResolutionService;
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

        log.info(
                "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}",
                jobId,
                correlationId,
                chapterId,
                bookId
        );

        StepResult result = execute(jobId, chapterId);

        if (result.success()) {
            eventPublisher.publishEvent(new ChapterCollectivesResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("rawCollectivesProcessed", 0),
                    result.counts().getOrDefault("chapterCollectivesCreated", 0)
            ));
        } else {
            log.error(
                    "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Failed: jobId={}, correlationId={}, chapterId={}, bookId={}",
                    jobId, correlationId, chapterId, bookId
            );
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    STAGE_CHAPTER_COLLECTIVE_RESOLUTION, result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    STAGE_CHAPTER_COLLECTIVE_RESOLUTION + " failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        stageSupport.updateJobStatus(jobId, IngestionStatus.RESOLVING_COLLECTIVES, "Resolving collective entities for chapter...");

        try {
            log.info(
                    "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterCollectiveResolutionResult response = chapterCollectiveResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterCollectiveCount={}",
                        jobId,
                        chapterId,
                        response.rawCollectivesProcessed(),
                        response.chapterCollectivesCreated()
                );
            } else {
                log.warn(
                        "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterCollectiveCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawCollectivesProcessed(),
                        response.chapterCollectivesCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(STAGE_CHAPTER_COLLECTIVE_RESOLUTION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawCollectivesProcessed", response.rawCollectivesProcessed(),
                            "chapterCollectivesCreated", response.chapterCollectivesCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(STAGE_CHAPTER_COLLECTIVE_RESOLUTION,
                    PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        }
    }
}
