package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.events.BookObjectsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookObjectReductionHandler {

    static final String STAGE_BOOK_OBJECT_REDUCTION = "BOOK_OBJECT_REDUCTION";

    private final BookObjectReductionService bookObjectReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public BookObjectReductionHandler(
            BookObjectReductionService bookObjectReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookObjectReductionService = bookObjectReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterObjectsResolved(ChapterObjectsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        stageSupport.runStage(
                this,
                STAGE_BOOK_OBJECT_REDUCTION,
                jobId,
                correlationId,
                chapterId,
                () -> {
            log.info("[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);
            BookObjectResolutionResult response = bookObjectReductionService.resolveBook(bookId);

            if (response.success()) {
                log.info(
                        "[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterObjectCount={}, bookObjectCount={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.chapterObjectsProcessed(),
                        response.bookObjectsCreated()
                );
            } else {
                log.warn(
                        "[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterObjectCount={}, bookObjectCount={}, reason={}",
                        jobId,
                        chapterId,
                        bookId,
                        response.chapterObjectsProcessed(),
                        response.bookObjectsCreated(),
                        response.message()
                );
            }

            eventPublisher.publishEvent(new BookObjectsReducedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    response.success(),
                    response.chapterObjectsProcessed(),
                    response.bookObjectsCreated()
            ));
            return null;
                },
                e -> e instanceof BookReductionClaimUnavailableException
        );
    }
}
