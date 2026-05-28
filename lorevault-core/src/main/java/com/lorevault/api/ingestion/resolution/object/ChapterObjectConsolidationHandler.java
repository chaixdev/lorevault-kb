package com.lorevault.api.ingestion.resolution.object;

import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ForStage(StageKey.CHAPTER_OBJECT_CONSOLIDATION)
public class ChapterObjectConsolidationHandler implements ChapterObjectConsolidationOperation {

    private final ChapterObjectConsolidationService chapterObjectConsolidationService;

    public ChapterObjectConsolidationHandler(
            ChapterObjectConsolidationService chapterObjectConsolidationService
    ) {
        this.chapterObjectConsolidationService = chapterObjectConsolidationService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[CHAPTER_OBJECT_CONSOLIDATION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterObjectConsolidationResult response = chapterObjectConsolidationService.consolidateChapter(chapterId);

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
            return StepResult.success(StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawObjectsProcessed", response.rawObjectsProcessed(),
                            "chapterObjectsCreated", response.chapterObjectsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_OBJECT_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
