package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class BookObjectReductionHandler implements BookObjectReductionOperation {

    private static final String CLAIM_LANE = "BOOK_OBJECT_REDUCTION";

    private final BookObjectReductionService bookObjectReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookReductionClaimService bookReductionClaimService;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public BookObjectReductionHandler(
            BookObjectReductionService bookObjectReductionService,
            ApplicationEventPublisher eventPublisher,
            BookReductionClaimService bookReductionClaimService,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.bookObjectReductionService = bookObjectReductionService;
        this.eventPublisher = eventPublisher;
        this.bookReductionClaimService = bookReductionClaimService;
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) {
            return;
        }

        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        if (bookId != null && stageOutputRepo.existsByBookIdAndStep(bookId, event.getStage())) {
            stageRepo.setSkipped(jobId, event.getStage());
            eventPublisher.publishEvent(new StageCompletedEvent(
                    this, jobId, chapterId, bookId, event.getStage(),
                    StepResult.success(event.getStage().name(),
                            "Skipped — already completed", 0L)));
            log.info("[SKIPPED] Book stage {} already completed for book {}", event.getStage(), bookId);
            return;
        }

        StepResult result = execute(jobId, bookId);

        eventPublisher.publishEvent(new StageCompletedEvent(
                this, jobId, chapterId, bookId, event.getStage(), result));
    }

    @Override
    public StepResult execute(UUID jobId, UUID bookId) {
        long start = System.currentTimeMillis();

        if (!bookReductionClaimService.tryAcquireClaim(bookId, CLAIM_LANE)) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[BOOK_OBJECT_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(StageKey.BOOK_OBJECT_REDUCTION.name(),
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
                return StepResult.success(StageKey.BOOK_OBJECT_REDUCTION.name(),
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
                return StepResult.success(StageKey.BOOK_OBJECT_REDUCTION.name(),
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
                    ? StepResult.retryableFailure(StageKey.BOOK_OBJECT_REDUCTION.name(),
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.BOOK_OBJECT_REDUCTION.name(),
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
