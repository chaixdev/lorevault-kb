package com.lorevault.api.ingestion.resolution.collective;

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
public class ChapterCollectiveResolutionHandler implements ChapterCollectiveResolutionOperation {

    private final ChapterCollectiveResolutionService chapterCollectiveResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public ChapterCollectiveResolutionHandler(
            ChapterCollectiveResolutionService chapterCollectiveResolutionService,
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.chapterCollectiveResolutionService = chapterCollectiveResolutionService;
        this.eventPublisher = eventPublisher;
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
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

        log.info("[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Started: jobId={}, chapterId={}",
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
                    "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Processing: jobId={}, chapterId={}",
                    jobId,
                    chapterId
            );

            ChapterCollectiveResolutionResult response = chapterCollectiveResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterCollectiveCount={}",
                        jobId,
                        chapterId,
                        response.rawCollectivesProcessed(),
                        response.chapterCollectivesCreated()
                );
            } else {
                log.warn(
                        "[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterCollectiveCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawCollectivesProcessed(),
                        response.chapterCollectivesCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_COLLECTIVE_RESOLUTION,
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawCollectivesProcessed", response.rawCollectivesProcessed(),
                            "chapterCollectivesCreated", response.chapterCollectivesCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:COLLECTIVE] [CHAPTER_COLLECTIVE_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_COLLECTIVE_RESOLUTION,
                    sanitizeMessage(e), elapsed);
        }
    }
}
