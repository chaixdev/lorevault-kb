package com.lorevault.api.ingestion.resolution.individual;

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
@ForStage(StageKey.BOOK_INDIVIDUAL_REDUCTION)
public class BookIndividualReductionHandler implements BookIndividualReductionOperation {

    private static final String CLAIM_LANE = "BOOK_INDIVIDUAL_REDUCTION";

    private final BookIndividualReductionService bookIndividualReductionService;
    private final BookReductionClaimService bookReductionClaimService;

    public BookIndividualReductionHandler(
            BookIndividualReductionService bookIndividualReductionService,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookIndividualReductionService = bookIndividualReductionService;
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_INDIVIDUAL_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_INDIVIDUAL_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookIndividualResolutionResult response = bookIndividualReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_INDIVIDUAL_REDUCTION] Completed: jobId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                        jobId, bookId, response.chapterIndividualsProcessed(), response.bookIndividualsCreated()
                );
                return StepResult.success(StageKey.BOOK_INDIVIDUAL_REDUCTION,
                        String.format("Reduced %d chapter individuals into %d book individuals",
                                response.chapterIndividualsProcessed(), response.bookIndividualsCreated()),
                        Map.of("chapterIndividualsProcessed", response.chapterIndividualsProcessed(),
                                "bookIndividualsCreated", response.bookIndividualsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_INDIVIDUAL_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_INDIVIDUAL_REDUCTION,
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
                    ? StepResult.retryableFailure(StageKey.BOOK_INDIVIDUAL_REDUCTION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_INDIVIDUAL_REDUCTION,
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
