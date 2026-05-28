package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.location.BookConsolidationClaimService;
import com.lorevault.api.ingestion.resolution.location.BookConsolidationClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION)
public class BookIndividualConsolidationHandler implements BookIndividualConsolidationOperation {

    private static final String CLAIM_LANE = "BOOK_INDIVIDUAL_CONSOLIDATION";

    private final BookIndividualConsolidationService bookIndividualConsolidationService;
    private final BookConsolidationClaimService bookConsolidationClaimService;

    public BookIndividualConsolidationHandler(
            BookIndividualConsolidationService bookIndividualConsolidationService,
            BookConsolidationClaimService bookConsolidationClaimService
    ) {
        this.bookIndividualConsolidationService = bookIndividualConsolidationService;
        this.bookConsolidationClaimService = bookConsolidationClaimService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookConsolidationClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_INDIVIDUAL_CONSOLIDATION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookIndividualConsolidationResult response = bookIndividualConsolidationService.consolidateBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_INDIVIDUAL_CONSOLIDATION] Completed: jobId={}, bookId={}, chapterIndividualCount={}, bookIndividualCount={}",
                        jobId, bookId, response.chapterIndividualsProcessed(), response.bookIndividualsCreated()
                );
                return StepResult.success(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
                        String.format("Reduced %d chapter individuals into %d book individuals",
                                response.chapterIndividualsProcessed(), response.bookIndividualsCreated()),
                        Map.of("chapterIndividualsProcessed", response.chapterIndividualsProcessed(),
                                "bookIndividualsCreated", response.bookIndividualsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_INDIVIDUAL_CONSOLIDATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
                        "Skipped — " + response.message(),
                        Map.of("chapterIndividualsProcessed", response.chapterIndividualsProcessed(),
                                "bookIndividualsCreated", response.bookIndividualsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_INDIVIDUAL_CONSOLIDATION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
                            sanitizeMessage(e), elapsed);
        } finally {
            bookConsolidationClaimService.releaseClaim(bookId, CLAIM_LANE);
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
        if (e instanceof BookConsolidationClaimUnavailableException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("API") || message.contains("timeout") || message.contains("rate limit") || message.contains("connection"));
    }
}
