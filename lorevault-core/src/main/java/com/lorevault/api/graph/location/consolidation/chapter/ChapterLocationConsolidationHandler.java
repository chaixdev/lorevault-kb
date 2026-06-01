package com.lorevault.api.graph.location.consolidation.chapter;

import static com.lorevault.api.common.ExceptionSanitizer.sanitize;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StepResult;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ForStage(StageKey.CHAPTER_LOCATION_CONSOLIDATION)
public class ChapterLocationConsolidationHandler implements StageOperation {

    private final ChapterLocationConsolidationService chapterLocationConsolidationService;

    public ChapterLocationConsolidationHandler(
            ChapterLocationConsolidationService chapterLocationConsolidationService
    ) {
        this.chapterLocationConsolidationService = chapterLocationConsolidationService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            ChapterLocationConsolidationResult response = chapterLocationConsolidationService.consolidateChapter(ctx, chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_LOCATION_CONSOLIDATION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_LOCATION_CONSOLIDATION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawLocationsProcessed", response.rawLocationsProcessed(),
                            "chapterLocationsCreated", response.chapterLocationsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_LOCATION_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                            sanitize(e), elapsed)
                    : StepResult.failure(StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                            sanitize(e), elapsed);
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
        String message = e.getMessage();
        return message != null && (message.contains("API") || message.contains("timeout") || message.contains("rate limit") || message.contains("connection"));
    }
}
