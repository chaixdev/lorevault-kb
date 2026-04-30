package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChapterObjectResolutionHandler {

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

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        stageSupport.runStage(
                this,
                STAGE_CHAPTER_OBJECT_RESOLUTION,
                jobId,
                correlationId,
                chapterId,
                () -> {
            log.info("[CHAPTER_OBJECT_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);
            ChapterObjectResolutionResult response = chapterObjectResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_OBJECT_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterObjectCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_OBJECT_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterObjectCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated(),
                        response.message()
                );
            }

            eventPublisher.publishEvent(new ChapterObjectsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    response.success(),
                    response.rawObjectsProcessed(),
                    response.chapterObjectsCreated()
            ));
            return null;
                },
                e -> false
        );
    }
}
