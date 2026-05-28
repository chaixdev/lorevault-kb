package com.lorevault.api.ingestion.resolution.object;

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
@ForStage(StageKey.BOOK_OBJECT_REDUCTION)
public class BookObjectReductionHandler implements BookObjectReductionOperation {

    private static final String CLAIM_LANE = "BOOK_OBJECT_REDUCTION";

    private final BookObjectReductionService bookObjectReductionService;
    private final BookReductionClaimService bookReductionClaimService;

    public BookObjectReductionHandler(
            BookObjectReductionService bookObjectReductionService,
            BookReductionClaimService bookReductionClaimService
    ) {
        this.bookObjectReductionService = bookObjectReductionService;
        this.bookReductionClaimService = bookReductionClaimService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID bookId = ctx.bookId();
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_OBJECT_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_OBJECT_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookObjectResolutionResult response = bookObjectReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:OBJECT] [BOOK_OBJECT_REDUCTION] Completed: jobId={}, bookId={}, chapterObjectCount={}, bookObjectCount={}",
                        jobId, bookId, response.chapterObjectsProcessed(), response.bookObjectsCreated()
                );
                return StepResult.success(StageKey.BOOK_OBJECT_REDUCTION,
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
                return StepResult.success(StageKey.BOOK_OBJECT_REDUCTION,
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
                    ? StepResult.retryableFailure(StageKey.BOOK_OBJECT_REDUCTION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_OBJECT_REDUCTION,
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
