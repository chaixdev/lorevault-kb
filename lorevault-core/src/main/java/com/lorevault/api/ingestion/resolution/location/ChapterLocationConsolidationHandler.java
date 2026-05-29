package com.lorevault.api.ingestion.resolution.location;

import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ForStage(StageKey.CHAPTER_LOCATION_CONSOLIDATION)
public class ChapterLocationConsolidationHandler implements ChapterLocationConsolidationOperation {

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
            ChapterLocationConsolidationResult response = chapterLocationConsolidationService.consolidateChapter(chapterId);

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
            return StepResult.failure(StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
