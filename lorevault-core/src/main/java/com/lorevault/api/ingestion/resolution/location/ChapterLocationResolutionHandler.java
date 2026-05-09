package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
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
public class ChapterLocationResolutionHandler implements ChapterLocationResolutionOperation {

    private final ChapterLocationResolutionService chapterLocationResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChapterLocationResolutionHandler(
            ChapterLocationResolutionService chapterLocationResolutionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterLocationResolutionService = chapterLocationResolutionService;
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

        log.info("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Started: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);

        StepResult result = execute(jobId, chapterId);

        if (result.success()) {
            eventPublisher.publishEvent(new ChapterLocationsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("rawLocationsProcessed", 0),
                    result.counts().getOrDefault("chapterLocationsCreated", 0)
            ));
        } else {
            log.error("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Failed: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    "CHAPTER_LOCATION_RESOLUTION", result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    "CHAPTER_LOCATION_RESOLUTION failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        stageSupport.updateJobStatus(jobId, IngestionStatus.RESOLVING_LOCATIONS, "Resolving location entities for chapter...");

        try {
            ChapterLocationResolutionResult response = chapterLocationResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated()
                );
            } else {
                log.warn(
                        "[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success("CHAPTER_LOCATION_RESOLUTION",
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawLocationsProcessed", response.rawLocationsProcessed(),
                            "chapterLocationsCreated", response.chapterLocationsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure("CHAPTER_LOCATION_RESOLUTION",
                    PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        }
    }
}
