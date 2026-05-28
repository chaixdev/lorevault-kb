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
@ForStage(StageKey.CHAPTER_OBJECT_RESOLUTION)
public class ChapterObjectResolutionHandler implements ChapterObjectResolutionOperation {

    private final ChapterObjectResolutionService chapterObjectResolutionService;

    public ChapterObjectResolutionHandler(
            ChapterObjectResolutionService chapterObjectResolutionService
    ) {
        this.chapterObjectResolutionService = chapterObjectResolutionService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[CHAPTER_OBJECT_RESOLUTION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterObjectResolutionResult response = chapterObjectResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_OBJECT_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_OBJECT_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_OBJECT_RESOLUTION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawObjectsProcessed", response.rawObjectsProcessed(),
                            "chapterObjectsCreated", response.chapterObjectsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_OBJECT_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_OBJECT_RESOLUTION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
