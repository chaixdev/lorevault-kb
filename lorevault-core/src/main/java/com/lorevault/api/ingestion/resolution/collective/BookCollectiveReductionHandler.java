package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.events.BookCollectivesReducedEvent;
import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
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
public class BookCollectiveReductionHandler {

    static final String STAGE_BOOK_COLLECTIVE_REDUCTION = "BOOK_COLLECTIVE_REDUCTION";

    private final BookCollectiveReductionService bookCollectiveReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public BookCollectiveReductionHandler(
            BookCollectiveReductionService bookCollectiveReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookCollectiveReductionService = bookCollectiveReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterCollectivesResolved(ChapterCollectivesResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        stageSupport.runStage(
                this,
                STAGE_BOOK_COLLECTIVE_REDUCTION,
                jobId,
                correlationId,
                chapterId,
                () -> {
                    log.info(
                            "[LANE:COLLECTIVE] [BOOK_COLLECTIVE_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}",
                            jobId,
                            correlationId,
                            chapterId,
                            bookId
                    );
                    BookCollectiveResolutionResult response = bookCollectiveReductionService.resolveBook(bookId);

                    if (response.success()) {
                        log.info(
                                "[LANE:COLLECTIVE] [BOOK_COLLECTIVE_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterCollectiveCount={}, bookCollectiveCount={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.chapterCollectivesProcessed(),
                                response.bookCollectivesCreated()
                        );
                    } else {
                        log.warn(
                                "[LANE:COLLECTIVE] [BOOK_COLLECTIVE_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterCollectiveCount={}, bookCollectiveCount={}, reason={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.chapterCollectivesProcessed(),
                                response.bookCollectivesCreated(),
                                response.message()
                        );
                    }

                    eventPublisher.publishEvent(new BookCollectivesReducedEvent(
                            this,
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            response.success(),
                            response.chapterCollectivesProcessed(),
                            response.bookCollectivesCreated()
                    ));
                    return null;
                },
                e -> e instanceof BookReductionClaimUnavailableException
        );
    }
}
