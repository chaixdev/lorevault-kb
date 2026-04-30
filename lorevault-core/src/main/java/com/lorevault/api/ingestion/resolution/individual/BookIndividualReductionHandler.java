package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class BookIndividualReductionHandler {

    static final String STAGE_BOOK_INDIVIDUAL_REDUCTION = "BOOK_INDIVIDUAL_REDUCTION";

    private final BookIndividualReductionService bookIndividualReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public BookIndividualReductionHandler(
            BookIndividualReductionService bookIndividualReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookIndividualReductionService = bookIndividualReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionTaskExecutor")
    @EventListener
    public void handleChapterIndividualsResolved(ChapterIndividualsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        stageSupport.runStage(
                this,
                STAGE_BOOK_INDIVIDUAL_REDUCTION,
                jobId,
                correlationId,
                chapterId,
                () -> {
                    log.info("[BOOK_INDIVIDUAL_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);
                    BookIndividualResolutionResult response = bookIndividualReductionService.resolveBook(bookId);

                    if (response.success()) {
                        log.info(
                                "[BOOK_INDIVIDUAL_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.chapterIndividualsProcessed(),
                                response.bookIndividualsCreated()
                        );
                    } else {
                        log.warn(
                                "[BOOK_INDIVIDUAL_REDUCTION] Skipped: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}, reason={}",
                                jobId,
                                chapterId,
                                bookId,
                                response.chapterIndividualsProcessed(),
                                response.bookIndividualsCreated(),
                                response.message()
                        );
                    }

                    eventPublisher.publishEvent(new BookIndividualsReducedEvent(
                            this,
                            jobId,
                            chapterId,
                            bookId,
                            response.success(),
                            response.chapterIndividualsProcessed(),
                            response.bookIndividualsCreated()
                    ));
                    return null;
                },
                e -> e instanceof BookReductionClaimUnavailableException
        );
    }
}
