package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.BOOK_COLLECTIVE_REDUCTION)
public class BookCollectiveReductionHandler implements BookCollectiveReductionOperation {

    private static final String CLAIM_LANE = "BOOK_COLLECTIVE_REDUCTION";

    private final BookCollectiveReductionService bookCollectiveReductionService;
    private final BookReductionClaimService bookReductionClaimService;

    public BookCollectiveReductionHandler(
            BookCollectiveReductionService bookCollectiveReductionService,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookCollectiveReductionService = bookCollectiveReductionService;
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_COLLECTIVE_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_COLLECTIVE_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookCollectiveResolutionResult response = bookCollectiveReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_COLLECTIVE_REDUCTION] Completed: jobId={}, bookId={}, chapterCollectiveCount={}, bookCollectiveCount={}",
                        jobId, bookId, response.chapterCollectivesProcessed(), response.bookCollectivesCreated()
                );
                return StepResult.success(StageKey.BOOK_COLLECTIVE_REDUCTION,
                        String.format("Reduced %d chapter collectives into %d book collectives",
                                response.chapterCollectivesProcessed(), response.bookCollectivesCreated()),
                        Map.of("chapterCollectivesProcessed", response.chapterCollectivesProcessed(),
                                "bookCollectivesCreated", response.bookCollectivesCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_COLLECTIVE_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_COLLECTIVE_REDUCTION,
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
                    ? StepResult.retryableFailure(StageKey.BOOK_COLLECTIVE_REDUCTION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_COLLECTIVE_REDUCTION,
                            sanitizeMessage(e), elapsed);
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
