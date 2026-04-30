package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
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
public class BookLocationReductionHandler {

    static final String STAGE_BOOK_LOCATION_REDUCTION = "BOOK_LOCATION_REDUCTION";

    private final BookLocationReductionService bookLocationReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public BookLocationReductionHandler(
            BookLocationReductionService bookLocationReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookLocationReductionService = bookLocationReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterLocationsResolved(ChapterLocationsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        stageSupport.runStage(
                this,
                STAGE_BOOK_LOCATION_REDUCTION,
                jobId,
                correlationId,
                chapterId,
                () -> {
                    log.info("[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);
                    BookLocationResolutionResult response = bookLocationReductionService.resolveBook(bookId);

                    if (response.success()) {
                        log.info(
                                "[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.chapterLocationsProcessed(),
                                response.bookLocationsCreated()
                        );
                    } else {
                        log.warn(
                                "[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}, reason={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.chapterLocationsProcessed(),
                                response.bookLocationsCreated(),
                                response.message()
                        );
                    }

                    eventPublisher.publishEvent(new BookLocationsReducedEvent(
                            this,
                            jobId,
                            correlationId,
                            chapterId,
                            bookId,
                            response.success(),
                            response.chapterLocationsProcessed(),
                            response.bookLocationsCreated()
                    ));
                    return null;
                },
                e -> e instanceof BookReductionClaimUnavailableException
        );
    }
}
