package com.lorevault.api.graph.location.consolidation.book;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import static com.lorevault.api.common.ExceptionSanitizer.sanitize;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;
import com.lorevault.api.orchestration.consolidation.BookConsolidationClaimService;
import com.lorevault.api.orchestration.consolidation.BookConsolidationClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.BOOK_LOCATION_CONSOLIDATION)
public class BookLocationConsolidationHandler implements StageOperation {

    private static final String CLAIM_LANE = "BOOK_LOCATION_CONSOLIDATION";

    private final BookLocationConsolidationService bookLocationConsolidationService;
    private final BookConsolidationClaimService bookConsolidationClaimService;

    public BookLocationConsolidationHandler(
            BookLocationConsolidationService bookLocationConsolidationService,
            BookConsolidationClaimService bookConsolidationClaimService
    ) {
        this.bookLocationConsolidationService = bookLocationConsolidationService;
        this.bookConsolidationClaimService = bookConsolidationClaimService;
    }

    @Override
    public StageResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookConsolidationClaimService.tryAcquireClaim(bookId, CLAIM_LANE, ctx.stageId())) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_LOCATION_CONSOLIDATION] Claim contention: jobId={}, bookId={}", jobId, bookId);
            return StageResult.retryableFailure(StageKey.BOOK_LOCATION_CONSOLIDATION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookLocationConsolidationResult response = bookLocationConsolidationService.consolidateBook(ctx, bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_LOCATION_CONSOLIDATION] Completed: jobId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                        jobId, bookId, response.chapterLocationsProcessed(), response.bookLocationsCreated()
                );
                return StageResult.success(StageKey.BOOK_LOCATION_CONSOLIDATION,
                        String.format("Reduced %d chapter locations into %d book locations",
                                response.chapterLocationsProcessed(), response.bookLocationsCreated()),
                        Map.of("chapterLocationsProcessed", response.chapterLocationsProcessed(),
                                "bookLocationsCreated", response.bookLocationsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_LOCATION_CONSOLIDATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StageResult.success(StageKey.BOOK_LOCATION_CONSOLIDATION,
                        "Skipped — " + response.message(),
                        Map.of("chapterLocationsProcessed", response.chapterLocationsProcessed(),
                                "bookLocationsCreated", response.bookLocationsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_LOCATION_CONSOLIDATION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StageResult.retryableFailure(StageKey.BOOK_LOCATION_CONSOLIDATION,
                            sanitize(e), elapsed)
                    : StageResult.failure(StageKey.BOOK_LOCATION_CONSOLIDATION,
                            sanitize(e), elapsed);
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
