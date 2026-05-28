package com.lorevault.api.ingestion.resolution.location;

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
@ForStage(StageKey.CHAPTER_LOCATION_RESOLUTION)
public class ChapterLocationResolutionHandler implements ChapterLocationResolutionOperation {

    private final ChapterLocationResolutionService chapterLocationResolutionService;

    public ChapterLocationResolutionHandler(
            ChapterLocationResolutionService chapterLocationResolutionService
    ) {
        this.chapterLocationResolutionService = chapterLocationResolutionService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            ChapterLocationResolutionResult response = chapterLocationResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[CHAPTER_LOCATION_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated()
                );
            } else {
                log.warn(
                        "[CHAPTER_LOCATION_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_LOCATION_RESOLUTION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawLocationsProcessed", response.rawLocationsProcessed(),
                            "chapterLocationsCreated", response.chapterLocationsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_LOCATION_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_LOCATION_RESOLUTION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
