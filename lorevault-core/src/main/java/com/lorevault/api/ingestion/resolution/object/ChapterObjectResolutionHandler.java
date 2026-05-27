package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Component
@Slf4j
public class ChapterObjectResolutionHandler implements ChapterObjectResolutionOperation {

    private final ChapterObjectResolutionService chapterObjectResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public ChapterObjectResolutionHandler(
            ChapterObjectResolutionService chapterObjectResolutionService,
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.chapterObjectResolutionService = chapterObjectResolutionService;
        this.eventPublisher = eventPublisher;
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        // 0. Stage key guard: reject events for other stages
        if (event.getStage() != StageKey.CHAPTER_OBJECT_RESOLUTION) return;

        // 1. Guard: only one thread executes at a time
        if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) {
            return;
        }

        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();

        // 2. Idempotency: does StageOutput already exist?
        if (stageOutputRepo.existsByChapterIdAndStep(chapterId, event.getStage())) {
            stageRepo.setSkipped(jobId, event.getStage());
            eventPublisher.publishEvent(new StageCompletedEvent(
                    this, jobId, chapterId, event.getStage(),
                    StepResult.success(event.getStage(),
                            "Skipped \u2014 already completed", 0L)));
            log.info("[SKIPPED] Stage {} already completed for chapter {}", event.getStage(), chapterId);
            return;
        }

        log.info("[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Started: jobId={}, chapterId={}",
                jobId, chapterId);

        // 3. Do the work
        StepResult result = execute(jobId, chapterId);

        // 4. Emit completion — coordinator handles downstream
        eventPublisher.publishEvent(new StageCompletedEvent(
                this, jobId, chapterId, event.getStage(), result));
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        try {
            log.info(
                    "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterObjectResolutionResult response = chapterObjectResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}",
                        jobId,
                        chapterId,
                        response.rawObjectsProcessed(),
                        response.chapterObjectsCreated()
                );
            } else {
                log.warn(
                        "[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterObjectCount={}, reason={}",
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
            log.error("[LANE:OBJECT] [CHAPTER_OBJECT_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_OBJECT_RESOLUTION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
