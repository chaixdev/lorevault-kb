package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import static com.lorevault.api.ingestion.infrastructure.ExceptionSanitizer.sanitizeMessage;

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
public class ChapterLocationResolutionHandler implements ChapterLocationResolutionOperation {

    private final ChapterLocationResolutionService chapterLocationResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public ChapterLocationResolutionHandler(
            ChapterLocationResolutionService chapterLocationResolutionService,
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.chapterLocationResolutionService = chapterLocationResolutionService;
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
                    StepResult.success(event.getStage().name(),
                            "Skipped \u2014 already completed", 0L)));
            log.info("[SKIPPED] Stage {} already completed for chapter {}", event.getStage(), chapterId);
            return;
        }

        log.info("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Started: jobId={}, chapterId={}",
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
            ChapterLocationResolutionResult response = chapterLocationResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated()
                );
            } else {
                log.warn(
                        "[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterLocationCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawLocationsProcessed(),
                        response.chapterLocationsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_LOCATION_RESOLUTION.name(),
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawLocationsProcessed", response.rawLocationsProcessed(),
                            "chapterLocationsCreated", response.chapterLocationsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:LOCATION] [CHAPTER_LOCATION_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_LOCATION_RESOLUTION.name(),
                    sanitizeMessage(e), elapsed);
        }
    }
}
