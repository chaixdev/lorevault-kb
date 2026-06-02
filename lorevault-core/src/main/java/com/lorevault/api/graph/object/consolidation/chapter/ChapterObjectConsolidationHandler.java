package com.lorevault.api.graph.object.consolidation.chapter;

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
@ForStage(StageKey.CHAPTER_OBJECT_CONSOLIDATION)
public class ChapterObjectConsolidationHandler implements StageOperation {

    private final ChapterObjectConsolidationService chapterObjectConsolidationService;

    public ChapterObjectConsolidationHandler(
            ChapterObjectConsolidationService chapterObjectConsolidationService
    ) {
        this.chapterObjectConsolidationService = chapterObjectConsolidationService;
    }

    @Override
    public StageResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[CHAPTER_OBJECT_CONSOLIDATION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterObjectConsolidationResult response = chapterObjectConsolidationService.consolidateChapter(ctx, chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_OBJECT_CONSOLIDATION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_OBJECT_CONSOLIDATION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StageResult.success(StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawObjectsProcessed", response.rawObjectsProcessed(),
                            "chapterObjectsCreated", response.chapterObjectsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_OBJECT_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StageResult.retryableFailure(StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                            sanitize(e), elapsed)
                    : StageResult.failure(StageKey.CHAPTER_OBJECT_CONSOLIDATION,
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
