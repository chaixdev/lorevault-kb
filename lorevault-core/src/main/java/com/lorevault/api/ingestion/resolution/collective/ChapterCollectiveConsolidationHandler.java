package com.lorevault.api.ingestion.resolution.collective;

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
@ForStage(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION)
public class ChapterCollectiveConsolidationHandler implements ChapterCollectiveConsolidationOperation {

    private final ChapterCollectiveConsolidationService chapterCollectiveConsolidationService;

    public ChapterCollectiveConsolidationHandler(
            ChapterCollectiveConsolidationService chapterCollectiveConsolidationService
    ) {
        this.chapterCollectiveConsolidationService = chapterCollectiveConsolidationService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[CHAPTER_COLLECTIVE_CONSOLIDATION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterCollectiveConsolidationResult response = chapterCollectiveConsolidationService.consolidateChapter(chapterId);

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
            return StepResult.success(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawCollectivesProcessed", response.rawCollectivesProcessed(),
                            "chapterCollectivesCreated", response.chapterCollectivesCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_COLLECTIVE_CONSOLIDATION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
