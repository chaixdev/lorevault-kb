package com.lorevault.api.ingestion.resolution.collective;

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
@ForStage(StageKey.BOOK_COLLECTIVE_CONSOLIDATION)
public class BookCollectiveConsolidationHandler implements BookCollectiveConsolidationOperation {

    private static final String CLAIM_LANE = "BOOK_COLLECTIVE_CONSOLIDATION";

    private final BookCollectiveConsolidationService bookCollectiveConsolidationService;
    private final BookConsolidationClaimService bookConsolidationClaimService;

    public BookCollectiveConsolidationHandler(
            BookCollectiveConsolidationService bookCollectiveConsolidationService,
            BookConsolidationClaimService bookConsolidationClaimService
    ) {
        this.bookCollectiveConsolidationService = bookCollectiveConsolidationService;
        this.bookConsolidationClaimService = bookConsolidationClaimService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookConsolidationClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_COLLECTIVE_CONSOLIDATION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookCollectiveConsolidationResult response = bookCollectiveConsolidationService.consolidateBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_COLLECTIVE_CONSOLIDATION] Completed: jobId={}, bookId={}, chapterCollectiveCount={}, bookCollectiveCount={}",
                        jobId, bookId, response.chapterCollectivesProcessed(), response.bookCollectivesCreated()
                );
                return StepResult.success(StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
                        String.format("Reduced %d chapter collectives into %d book collectives",
                                response.chapterCollectivesProcessed(), response.bookCollectivesCreated()),
                        Map.of("chapterCollectivesProcessed", response.chapterCollectivesProcessed(),
                                "bookCollectivesCreated", response.bookCollectivesCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_COLLECTIVE_CONSOLIDATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
                        "Skipped — " + response.message(),
                        Map.of("chapterCollectivesProcessed", response.chapterCollectivesProcessed(),
                                "bookCollectivesCreated", response.bookCollectivesCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_COLLECTIVE_CONSOLIDATION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
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
