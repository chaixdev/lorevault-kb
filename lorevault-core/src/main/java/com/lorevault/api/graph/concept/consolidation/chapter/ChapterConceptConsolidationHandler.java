package com.lorevault.api.graph.concept.consolidation.chapter;

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
@ForStage(StageKey.CHAPTER_CONCEPT_CONSOLIDATION)
public class ChapterConceptConsolidationHandler implements StageOperation {

    private final ChapterConceptConsolidationService chapterConceptConsolidationService;

    public ChapterConceptConsolidationHandler(
            ChapterConceptConsolidationService chapterConceptConsolidationService
    ) {
        this.chapterConceptConsolidationService = chapterConceptConsolidationService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[CHAPTER_CONCEPT_CONSOLIDATION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterConceptConsolidationResult response = chapterConceptConsolidationService.consolidateChapter(ctx, chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_CONCEPT_CONSOLIDATION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterConceptCount={}",
                        jobId,
                        chapterId,
                        response.rawConceptsProcessed(),
                        response.chapterConceptsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_CONCEPT_CONSOLIDATION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterConceptCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawConceptsProcessed(),
                        response.chapterConceptsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_CONCEPT_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawConceptsProcessed", response.rawConceptsProcessed(),
                            "chapterConceptsCreated", response.chapterConceptsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_CONCEPT_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.CHAPTER_CONCEPT_CONSOLIDATION,
                            sanitize(e), elapsed)
                    : StepResult.failure(StageKey.CHAPTER_CONCEPT_CONSOLIDATION,
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
        return message != null && (message.contains("API") || message.contains("timeout")
                || message.contains("rate limit") || message.contains("connection"));
    }
}
