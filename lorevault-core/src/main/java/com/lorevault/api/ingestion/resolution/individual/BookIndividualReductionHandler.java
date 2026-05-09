package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class BookIndividualReductionHandler implements BookIndividualReductionOperation {

    static final String STAGE_BOOK_INDIVIDUAL_REDUCTION = "BOOK_INDIVIDUAL_REDUCTION";
    private static final String CLAIM_LANE = "BOOK_INDIVIDUAL_REDUCTION";

    private final BookIndividualReductionService bookIndividualReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;
    private final BookReductionClaimService bookReductionClaimService;

    public BookIndividualReductionHandler(
            BookIndividualReductionService bookIndividualReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookIndividualReductionService = bookIndividualReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterIndividualsResolved(ChapterIndividualsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info("[LANE:INDIVIDUAL] [BOOK_INDIVIDUAL_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);

        StepResult result = execute(jobId, bookId);

        if (result.success()) {
            log.info(
                    "[LANE:INDIVIDUAL] [BOOK_INDIVIDUAL_REDUCTION] Completed: jobId={}, chapterId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                    jobId,
                    chapterId,
                    bookId,
                    result.counts().getOrDefault("chapterIndividualsProcessed", 0),
                    result.counts().getOrDefault("bookIndividualsCreated", 0)
            );

            eventPublisher.publishEvent(new BookIndividualsReducedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("chapterIndividualsProcessed", 0),
                    result.counts().getOrDefault("bookIndividualsCreated", 0)
            ));
        } else {
            log.warn(
                    "[LANE:INDIVIDUAL] [BOOK_INDIVIDUAL_REDUCTION] Failed: jobId={}, chapterId={}, bookId={}, summary={}",
                    jobId, chapterId, bookId, result.summary()
            );

            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    STAGE_BOOK_INDIVIDUAL_REDUCTION, result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    STAGE_BOOK_INDIVIDUAL_REDUCTION + " failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID bookId) {
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_INDIVIDUAL_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(STAGE_BOOK_INDIVIDUAL_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            stageSupport.updateJobStatus(jobId, IngestionStatus.PERSISTING_DATA,
                    "Reducing chapter-level individuals to book-level individuals");

            BookIndividualResolutionResult response = bookIndividualReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:INDIVIDUAL] [BOOK_INDIVIDUAL_REDUCTION] Completed: jobId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                        jobId, bookId, response.chapterIndividualsProcessed(), response.bookIndividualsCreated()
                );
                return StepResult.success(STAGE_BOOK_INDIVIDUAL_REDUCTION,
                        String.format("Reduced %d chapter individuals into %d book individuals",
                                response.chapterIndividualsProcessed(), response.bookIndividualsCreated()),
                        Map.of("chapterIndividualsProcessed", response.chapterIndividualsProcessed(),
                                "bookIndividualsCreated", response.bookIndividualsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[LANE:INDIVIDUAL] [BOOK_INDIVIDUAL_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(STAGE_BOOK_INDIVIDUAL_REDUCTION,
                        "Skipped — " + response.message(),
                        Map.of("chapterIndividualsProcessed", response.chapterIndividualsProcessed(),
                                "bookIndividualsCreated", response.bookIndividualsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_INDIVIDUAL_REDUCTION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(STAGE_BOOK_INDIVIDUAL_REDUCTION,
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure(STAGE_BOOK_INDIVIDUAL_REDUCTION,
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        } finally {
            bookReductionClaimService.releaseClaim(bookId, CLAIM_LANE);
        }
    }

    private boolean isRetryableError(Exception e) {
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }
        if (e instanceof BookReductionClaimUnavailableException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("API") || message.contains("timeout") || message.contains("rate limit") || message.contains("connection"));
    }
}
