package com.lorevault.api.graph.concept.consolidation.book;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import com.lorevault.api.graph.location.consolidation.book.BookConsolidationClaimService;
import com.lorevault.api.graph.location.consolidation.book.BookConsolidationClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.BOOK_CONCEPT_CONSOLIDATION)
public class BookConceptConsolidationHandler implements BookConceptConsolidationOperation {

    private static final String CLAIM_LANE = "BOOK_CONCEPT_CONSOLIDATION";

    private final BookConceptConsolidationService bookConceptConsolidationService;
    private final BookConsolidationClaimService bookConsolidationClaimService;

    public BookConceptConsolidationHandler(
            BookConceptConsolidationService bookConceptConsolidationService,
            BookConsolidationClaimService bookConsolidationClaimService
    ) {
        this.bookConceptConsolidationService = bookConceptConsolidationService;
        this.bookConsolidationClaimService = bookConsolidationClaimService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookConsolidationClaimService.tryAcquireClaim(bookId, CLAIM_LANE, ctx.stageId())) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_CONCEPT_CONSOLIDATION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_CONCEPT_CONSOLIDATION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookConceptConsolidationResult response = bookConceptConsolidationService.consolidateBook(ctx, bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[BOOK_CONCEPT_CONSOLIDATION] Completed: jobId={}, bookId={}, chapterConceptCount={}, bookConceptCount={}",
                        jobId, bookId, response.chapterConceptsProcessed(), response.bookConceptsCreated()
                );
                return StepResult.success(StageKey.BOOK_CONCEPT_CONSOLIDATION,
                        String.format("Reduced %d chapter concepts into %d book concepts",
                                response.chapterConceptsProcessed(), response.bookConceptsCreated()),
                        Map.of("chapterConceptsProcessed", response.chapterConceptsProcessed(),
                                "bookConceptsCreated", response.bookConceptsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[BOOK_CONCEPT_CONSOLIDATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_CONCEPT_CONSOLIDATION,
                        "Skipped — " + response.message(),
                        Map.of("chapterConceptsProcessed", response.chapterConceptsProcessed(),
                                "bookConceptsCreated", response.bookConceptsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_CONCEPT_CONSOLIDATION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.BOOK_CONCEPT_CONSOLIDATION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_CONCEPT_CONSOLIDATION,
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
