package com.lorevault.api.graph.individual.consolidation.chapter;

import java.util.Map;
import java.util.UUID;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import lombok.extern.slf4j.Slf4j;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ForStage(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION)
public class ChapterIndividualConsolidationHandler implements ChapterIndividualConsolidationOperation {

    private final ChapterIndividualConsolidationService chapterIndividualConsolidationService;

    public ChapterIndividualConsolidationHandler(
            ChapterIndividualConsolidationService chapterIndividualConsolidationService
    ) {
        this.chapterIndividualConsolidationService = chapterIndividualConsolidationService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            ChapterIndividualConsolidationResult response = chapterIndividualConsolidationService.consolidateChapter(ctx, chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_INDIVIDUAL_CONSOLIDATION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_INDIVIDUAL_CONSOLIDATION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawIndividualsProcessed", response.rawIndividualsProcessed(),
                            "chapterIndividualsCreated", response.chapterIndividualsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_INDIVIDUAL_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
