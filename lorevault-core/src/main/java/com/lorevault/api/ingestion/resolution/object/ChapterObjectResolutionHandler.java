package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
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
public class ChapterObjectResolutionHandler implements ChapterObjectResolutionOperation {

    static final String STAGE_CHAPTER_OBJECT_RESOLUTION = "CHAPTER_OBJECT_RESOLUTION";

    private final ChapterObjectResolutionService chapterObjectResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChapterObjectResolutionHandler(
            ChapterObjectResolutionService chapterObjectResolutionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterObjectResolutionService = chapterObjectResolutionService;
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

        log.info("[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);

        StepResult result = execute(jobId, chapterId);

        if (result.success()) {
            eventPublisher.publishEvent(new ChapterObjectsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("rawObjectsProcessed", 0),
                    result.counts().getOrDefault("chapterObjectsCreated", 0)
            ));
        } else {
            log.error(
                    "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Failed: jobId={}, correlationId={}, chapterId={}, bookId={}",
                    jobId, correlationId, chapterId, bookId
            );
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    STAGE_CHAPTER_OBJECT_RESOLUTION, result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    STAGE_CHAPTER_OBJECT_RESOLUTION + " failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        stageSupport.updateJobStatus(jobId, IngestionStatus.RESOLVING_OBJECTS, "Resolving object entities for chapter...");

        try {
            log.info(
                    "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterObjectResolutionResult response = chapterObjectResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated()
                );
            } else {
                log.warn(
                        "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(STAGE_CHAPTER_OBJECT_RESOLUTION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawObjectsProcessed", response.rawObjectsProcessed(),
                            "chapterObjectsCreated", response.chapterObjectsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(STAGE_CHAPTER_OBJECT_RESOLUTION,
                    PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        }
    }
}
