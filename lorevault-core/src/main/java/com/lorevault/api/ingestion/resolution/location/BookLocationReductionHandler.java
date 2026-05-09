package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class BookLocationReductionHandler implements BookLocationReductionOperation {

    static final String STAGE_BOOK_LOCATION_REDUCTION = "BOOK_LOCATION_REDUCTION";
    private static final String CLAIM_LANE = "BOOK_LOCATION_REDUCTION";

    private final BookLocationReductionService bookLocationReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;
    private final BookReductionClaimService bookReductionClaimService;

    public BookLocationReductionHandler(
            BookLocationReductionService bookLocationReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookLocationReductionService = bookLocationReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterLocationsResolved(ChapterLocationsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info("[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);

        StepResult result = execute(jobId, bookId);

        if (result.success()) {
            eventPublisher.publishEvent(new BookLocationsReducedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("chapterLocationsProcessed", 0),
                    result.counts().getOrDefault("bookLocationsCreated", 0)
            ));
        } else {
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    STAGE_BOOK_LOCATION_REDUCTION, result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    STAGE_BOOK_LOCATION_REDUCTION + " failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID bookId) {
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_LOCATION_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(STAGE_BOOK_LOCATION_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            stageSupport.updateJobStatus(jobId, IngestionStatus.PERSISTING_DATA,
                    "Reducing chapter-level locations to book-level locations");

            BookLocationResolutionResult response = bookLocationReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Completed: jobId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                        jobId, bookId, response.chapterLocationsProcessed(), response.bookLocationsCreated()
                );
                return StepResult.success(STAGE_BOOK_LOCATION_REDUCTION,
                        String.format("Reduced %d chapter locations into %d book locations",
                                response.chapterLocationsProcessed(), response.bookLocationsCreated()),
                        Map.of("chapterLocationsProcessed", response.chapterLocationsProcessed(),
                                "bookLocationsCreated", response.bookLocationsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(STAGE_BOOK_LOCATION_REDUCTION,
                        "Skipped — " + response.message(),
                        Map.of("chapterLocationsProcessed", response.chapterLocationsProcessed(),
                                "bookLocationsCreated", response.bookLocationsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_LOCATION_REDUCTION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(STAGE_BOOK_LOCATION_REDUCTION,
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure(STAGE_BOOK_LOCATION_REDUCTION,
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
