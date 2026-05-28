package com.lorevault.api.ingestion.resolution.location;

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
@ForStage(StageKey.BOOK_LOCATION_REDUCTION)
public class BookLocationReductionHandler implements BookLocationReductionOperation {

    private static final String CLAIM_LANE = "BOOK_LOCATION_REDUCTION";

    private final BookLocationReductionService bookLocationReductionService;
    private final BookReductionClaimService bookReductionClaimService;

    public BookLocationReductionHandler(
            BookLocationReductionService bookLocationReductionService,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookLocationReductionService = bookLocationReductionService;
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_LOCATION_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_LOCATION_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookLocationResolutionResult response = bookLocationReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_LOCATION_REDUCTION] Completed: jobId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                        jobId, bookId, response.chapterLocationsProcessed(), response.bookLocationsCreated()
                );
                return StepResult.success(StageKey.BOOK_LOCATION_REDUCTION,
                        String.format("Reduced %d chapter locations into %d book locations",
                                response.chapterLocationsProcessed(), response.bookLocationsCreated()),
                        Map.of("chapterLocationsProcessed", response.chapterLocationsProcessed(),
                                "bookLocationsCreated", response.bookLocationsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_LOCATION_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_LOCATION_REDUCTION,
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
                    ? StepResult.retryableFailure(StageKey.BOOK_LOCATION_REDUCTION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_LOCATION_REDUCTION,
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
