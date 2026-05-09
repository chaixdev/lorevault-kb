package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.events.BookObjectsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
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
public class BookObjectReductionHandler implements BookObjectReductionOperation {

    static final String STAGE_BOOK_OBJECT_REDUCTION = "BOOK_OBJECT_REDUCTION";
    private static final String CLAIM_LANE = "BOOK_OBJECT_REDUCTION";

    private final BookObjectReductionService bookObjectReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;
    private final BookReductionClaimService bookReductionClaimService;

    public BookObjectReductionHandler(
            BookObjectReductionService bookObjectReductionService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookObjectReductionService = bookObjectReductionService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChapterObjectsResolved(ChapterObjectsResolvedEvent event) {
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        log.info("[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}", jobId, correlationId, chapterId, bookId);

        StepResult result = execute(jobId, bookId);

        if (result.success()) {
            eventPublisher.publishEvent(new BookObjectsReducedEvent(
                    this,
                    jobId,
                    correlationId,
                    chapterId,
                    bookId,
                    true,
                    result.counts().getOrDefault("chapterObjectsProcessed", 0),
                    result.counts().getOrDefault("bookObjectsCreated", 0)
            ));
        } else {
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId,
                    STAGE_BOOK_OBJECT_REDUCTION, result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    STAGE_BOOK_OBJECT_REDUCTION + " failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID bookId) {
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_OBJECT_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(STAGE_BOOK_OBJECT_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            stageSupport.updateJobStatus(jobId, IngestionStatus.PERSISTING_DATA,
                    "Reducing chapter-level objects to book-level objects");

            BookObjectResolutionResult response = bookObjectReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Completed: jobId={}, bookId={}, chapterObjectCount={}, bookObjectCount={}",
                        jobId, bookId, response.chapterObjectsProcessed(), response.bookObjectsCreated()
                );
                return StepResult.success(STAGE_BOOK_OBJECT_REDUCTION,
                        String.format("Reduced %d chapter objects into %d book objects",
                                response.chapterObjectsProcessed(), response.bookObjectsCreated()),
                        Map.of("chapterObjectsProcessed", response.chapterObjectsProcessed(),
                                "bookObjectsCreated", response.bookObjectsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(STAGE_BOOK_OBJECT_REDUCTION,
                        "Skipped — " + response.message(),
                        Map.of("chapterObjectsProcessed", response.chapterObjectsProcessed(),
                                "bookObjectsCreated", response.bookObjectsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_OBJECT_REDUCTION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(STAGE_BOOK_OBJECT_REDUCTION,
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure(STAGE_BOOK_OBJECT_REDUCTION,
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
