package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
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
public class ChapterCollectiveResolutionHandler {

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

        stageSupport.runStage(
                this,
                STAGE_CHAPTER_COLLECTIVE_RESOLUTION,
                jobId,
                correlationId,
                chapterId,
                () -> {
                    log.info(
                            "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}",
                            jobId,
                            correlationId,
                            chapterId,
                            bookId
                    );
                    ChapterCollectiveResolutionResult response = chapterCollectiveResolutionService.resolveChapter(chapterId);

                    if (response.success()) {
                        log.info(
                                "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterCollectiveCount={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.rawCollectivesProcessed(),
                                response.chapterCollectivesCreated()
                        );
                    } else {
                        log.warn(
                                "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Skipped: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterCollectiveCount={}, reason={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.rawCollectivesProcessed(),
                                response.chapterCollectivesCreated(),
                                response.message()
                        );
                    }

                    eventPublisher.publishEvent(new ChapterCollectivesResolvedEvent(
                            this,
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            response.success(),
                            response.rawCollectivesProcessed(),
                            response.chapterCollectivesCreated()
                    ));
                    return null;
                },
                e -> false
        );
    }
}
