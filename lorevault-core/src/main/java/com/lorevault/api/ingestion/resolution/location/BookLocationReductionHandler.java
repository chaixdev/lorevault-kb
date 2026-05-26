package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class BookLocationReductionHandler implements BookLocationReductionOperation {

    static final String STAGE_BOOK_LOCATION_REDUCTION = "BOOK_LOCATION_REDUCTION";
    private static final String CLAIM_LANE = "BOOK_LOCATION_REDUCTION";

    private final BookLocationReductionService bookLocationReductionService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookReductionClaimService bookReductionClaimService;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public BookLocationReductionHandler(
            BookLocationReductionService bookLocationReductionService,
            ApplicationEventPublisher eventPublisher,
            BookReductionClaimService bookReductionClaimService,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.bookLocationReductionService = bookLocationReductionService;
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
            log.warn("[BOOK_LOCATION_REDUCTION] Claim contention for bookId={}", bookId);
            return StepResult.retryableFailure(STAGE_BOOK_LOCATION_REDUCTION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            BookLocationResolutionResult response = bookLocationReductionService.resolveBook(bookId);

            long elapsed = System.currentTimeMillis() - start;

            if (response.success()) {
                log.info(
                        "[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Completed: jobId={}, bookId={}, chapterLocationCount={}, bookLocationCount={}",
                        jobId, bookId, response.chapterLocationsProcessed(), response.bookLocationsCreated()
                );
                return StepResult.success(STAGE_BOOK_LOCATION_REDUCTION,
                        String.format("Reduced %d chapter locations into %d book locations",
                                response.chapterLocationsProcessed(), response.bookLocationsCreated()),
                        Map.of("chapterLocationsProcessed", response.chapterLocationsProcessed(),
                                "bookLocationsCreated", response.bookLocationsCreated()),
                        elapsed);
            } else {
                log.warn(
                        "[LANE:LOCATION] [BOOK_LOCATION_REDUCTION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, response.message()
                );
                return StepResult.success(STAGE_BOOK_LOCATION_REDUCTION,
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
                    ? StepResult.retryableFailure(STAGE_BOOK_LOCATION_REDUCTION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(STAGE_BOOK_LOCATION_REDUCTION,
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
