package com.lorevault.api.ingestion.resolution.individual;

import java.util.Map;
import java.util.UUID;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import lombok.extern.slf4j.Slf4j;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ForStage(StageKey.CHAPTER_INDIVIDUAL_RESOLUTION)
public class ChapterIndividualResolutionHandler implements ChapterIndividualResolutionOperation {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;

    public ChapterIndividualResolutionHandler(
            ChapterIndividualResolutionService chapterIndividualResolutionService
    ) {
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            ChapterIndividualResolutionResult response = chapterIndividualResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_INDIVIDUAL_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_INDIVIDUAL_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_INDIVIDUAL_RESOLUTION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawIndividualsProcessed", response.rawIndividualsProcessed(),
                            "chapterIndividualsCreated", response.chapterIndividualsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_INDIVIDUAL_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_INDIVIDUAL_RESOLUTION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
