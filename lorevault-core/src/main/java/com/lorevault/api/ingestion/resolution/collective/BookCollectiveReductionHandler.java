package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.events.BookCollectivesReducedEvent;
import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
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
public class BookCollectiveReductionHandler implements BookCollectiveReductionOperation {

    static final String STAGE_BOOK_COLLECTIVE_REDUCTION = "BOOK_COLLECTIVE_REDUCTION";
    private static final String CLAIM_LANE = "BOOK_COLLECTIVE_REDUCTION";

    private final BookCollectiveReductionService bookCollectiveReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;
    private final BookReductionClaimService bookReductionClaimService;

    public BookCollectiveReductionHandler(
            BookCollectiveReductionService bookCollectiveReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookCollectiveReductionService = bookCollectiveReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterCollectivesResolved(ChapterCollectivesResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info(
                "[LANE:COLLECTIVE] [BOOK_COLLECTIVE_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}",
                jobId, correlationId, chapterId, bookId
        );

        StepResult result = execute(jobId, bookId);

        if (result.success()) {
            eventPublisher.publishEvent(new BookCollectivesReducedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("chapterCollectivesProcessed", 0),
                    result.counts().getOrDefault("bookCollectivesCreated", 0)
            ));
        } else {
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    STAGE_BOOK_COLLECTIVE_REDUCTION, result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    STAGE_BOOK_COLLECTIVE_REDUCTION + " failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID bookId) {
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_COLLECTIVE_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(STAGE_BOOK_COLLECTIVE_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            stageSupport.updateJobStatus(jobId, IngestionStatus.PERSISTING_DATA,
                    "Reducing chapter-level collectives to book-level collectives");

            BookCollectiveResolutionResult response = bookCollectiveReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:COLLECTIVE] [BOOK_COLLECTIVE_REDUCTION] Completed: jobId={}, bookId={}, chapterCollectiveCount={}, bookCollectiveCount={}",
                        jobId, bookId, response.chapterCollectivesProcessed(), response.bookCollectivesCreated()
                );
                return StepResult.success(STAGE_BOOK_COLLECTIVE_REDUCTION,
                        String.format("Reduced %d chapter collectives into %d book collectives",
                                response.chapterCollectivesProcessed(), response.bookCollectivesCreated()),
                        Map.of("chapterCollectivesProcessed", response.chapterCollectivesProcessed(),
                                "bookCollectivesCreated", response.bookCollectivesCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[LANE:COLLECTIVE] [BOOK_COLLECTIVE_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(STAGE_BOOK_COLLECTIVE_REDUCTION,
                        "Skipped — " + response.message(),
                        Map.of("chapterCollectivesProcessed", response.chapterCollectivesProcessed(),
                                "bookCollectivesCreated", response.bookCollectivesCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_COLLECTIVE_REDUCTION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(STAGE_BOOK_COLLECTIVE_REDUCTION,
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure(STAGE_BOOK_COLLECTIVE_REDUCTION,
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
