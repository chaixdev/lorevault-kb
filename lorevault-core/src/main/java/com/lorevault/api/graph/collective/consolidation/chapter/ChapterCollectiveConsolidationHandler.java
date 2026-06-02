package com.lorevault.api.graph.collective.consolidation.chapter;

import static com.lorevault.api.common.ExceptionSanitizer.sanitize;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StageResult;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ForStage(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION)
public class ChapterCollectiveConsolidationHandler implements StageOperation {

    private final ChapterCollectiveConsolidationService chapterCollectiveConsolidationService;

    public ChapterCollectiveConsolidationHandler(
            ChapterCollectiveConsolidationService chapterCollectiveConsolidationService
    ) {
        this.chapterCollectiveConsolidationService = chapterCollectiveConsolidationService;
    }

    @Override
    public StageResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[CHAPTER_COLLECTIVE_CONSOLIDATION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterCollectiveConsolidationResult response = chapterCollectiveConsolidationService.consolidateChapter(ctx, chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_COLLECTIVE_CONSOLIDATION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterCollectiveCount={}",
                        jobId,
                        chapterId,
                        response.rawCollectivesProcessed(),
                        response.chapterCollectivesCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_COLLECTIVE_CONSOLIDATION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterCollectiveCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawCollectivesProcessed(),
                        response.chapterCollectivesCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StageResult.success(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawCollectivesProcessed", response.rawCollectivesProcessed(),
                            "chapterCollectivesCreated", response.chapterCollectivesCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_COLLECTIVE_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StageResult.retryableFailure(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                            sanitize(e), elapsed)
                    : StageResult.failure(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
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
