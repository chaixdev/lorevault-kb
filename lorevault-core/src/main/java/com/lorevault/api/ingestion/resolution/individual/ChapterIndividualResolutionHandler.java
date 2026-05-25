package com.lorevault.api.ingestion.resolution.individual;

import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import static com.lorevault.api.ingestion.infrastructure.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Component
@Slf4j
public class ChapterIndividualResolutionHandler implements ChapterIndividualResolutionOperation {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public ChapterIndividualResolutionHandler(
            ChapterIndividualResolutionService chapterIndividualResolutionService,
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.chapterIndividualResolutionService = chapterIndividualResolutionService;
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

        log.info("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Started: jobId={}, chapterId={}",
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
            ChapterIndividualResolutionResult response = chapterIndividualResolutionService.resolveChapter(chapterId);

            if (response.success()) {
                log.info(
                        "[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Completed: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated()
                );
            } else {
                log.warn(
                        "[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Skipped: jobId={}, chapterId={}, mentionCount={}, chapterIndividualCount={}, reason={}",
                        jobId,
                        chapterId,
                        response.rawIndividualsProcessed(),
                        response.chapterIndividualsCreated(),
                        response.message()
                );
            }

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_INDIVIDUAL_RESOLUTION.name(),
                    response.message() != null ? response.message() : "Completed",
                    Map.of(
                            "rawIndividualsProcessed", response.rawIndividualsProcessed(),
                            "chapterIndividualsCreated", response.chapterIndividualsCreated()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION] Failed: jobId={}, chapterId={}", jobId, chapterId, e);
            return StepResult.failure(StageKey.CHAPTER_INDIVIDUAL_RESOLUTION.name(),
                    sanitizeMessage(e), elapsed);
        }
    }
}
