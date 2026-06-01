package com.lorevault.api.graph.object.consolidation.book;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import static com.lorevault.api.common.ExceptionSanitizer.sanitize;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import com.lorevault.api.orchestration.consolidation.BookConsolidationClaimService;
import com.lorevault.api.orchestration.consolidation.BookConsolidationClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.BOOK_OBJECT_CONSOLIDATION)
public class BookObjectConsolidationHandler implements StageOperation {

    private static final String CLAIM_LANE = "BOOK_OBJECT_CONSOLIDATION";

    private final BookObjectConsolidationService bookObjectConsolidationService;
    private final BookConsolidationClaimService bookConsolidationClaimService;

    public BookObjectConsolidationHandler(
            BookObjectConsolidationService bookObjectConsolidationService,
            BookConsolidationClaimService bookConsolidationClaimService
    ) {
        this.bookObjectConsolidationService = bookObjectConsolidationService;
        this.bookConsolidationClaimService = bookConsolidationClaimService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookConsolidationClaimService.tryAcquireClaim(bookId, CLAIM_LANE, ctx.stageId())) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_OBJECT_CONSOLIDATION] Claim contention: jobId={}, bookId={}", jobId, bookId);
            return StepResult.retryableFailure(StageKey.BOOK_OBJECT_CONSOLIDATION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookObjectConsolidationResult response = bookObjectConsolidationService.consolidateBook(ctx, bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:OBJECT] [BOOK_OBJECT_CONSOLIDATION] Completed: jobId={}, bookId={}, chapterObjectCount={}, bookObjectCount={}",
                        jobId, bookId, response.chapterObjectsProcessed(), response.bookObjectsCreated()
                );
                return StepResult.success(StageKey.BOOK_OBJECT_CONSOLIDATION,
                        String.format("Reduced %d chapter objects into %d book objects",
                                response.chapterObjectsProcessed(), response.bookObjectsCreated()),
                        Map.of("chapterObjectsProcessed", response.chapterObjectsProcessed(),
                                "bookObjectsCreated", response.bookObjectsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[LANE:OBJECT] [BOOK_OBJECT_CONSOLIDATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(StageKey.BOOK_OBJECT_CONSOLIDATION,
                        "Skipped — " + response.message(),
                        Map.of("chapterObjectsProcessed", response.chapterObjectsProcessed(),
                                "bookObjectsCreated", response.bookObjectsCreated()),
                        elapsed);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[BOOK_OBJECT_CONSOLIDATION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.BOOK_OBJECT_CONSOLIDATION,
                            sanitize(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_OBJECT_CONSOLIDATION,
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
