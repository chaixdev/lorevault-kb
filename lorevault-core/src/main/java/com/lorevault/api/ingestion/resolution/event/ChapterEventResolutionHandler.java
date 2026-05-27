package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventCorefModels;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;

import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.scene.SceneGraphRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/**
 * Async handler that orchestrates Stage 2 (LLM co-reference) and Stage 3 (ChapterEvent aggregation)
 * in response to a {@link StageTriggeredEvent}.
 *
 * <p>Implements {@link ChapterEventResolutionOperation} so the step-by-step execution controller or step-execution
 * endpoints can invoke event resolution directly without Spring event dispatch.
 *
 * <p>Uses stage orchestration graph for conditional execution, idempotency, and
 * completion signalling via {@link StageCompletedEvent}.
 */
@Component
@Slf4j
public class ChapterEventResolutionHandler implements ChapterEventResolutionOperation {

    private final EventCoreferenceService eventCoreferenceService;
    private final ChapterEventResolutionService chapterEventResolutionService;
    private final SceneGraphRepository sceneRepo;
    private final ChapterGraphRepository chapterRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public ChapterEventResolutionHandler(
            EventCoreferenceService eventCoreferenceService,
            ChapterEventResolutionService chapterEventResolutionService,
            SceneGraphRepository sceneRepo,
            ChapterGraphRepository chapterRepo,
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo
    ) {
        this.eventCoreferenceService = eventCoreferenceService;
        this.chapterEventResolutionService = chapterEventResolutionService;
        this.sceneRepo = sceneRepo;
        this.chapterRepo = chapterRepo;
        this.eventPublisher = eventPublisher;
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        // 0. Stage key guard: reject events for other stages
        if (event.getStage() != StageKey.CHAPTER_EVENT_RESOLUTION) return;

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

        log.info("[LANE:EVENT] [CHAPTER_EVENT_RESOLUTION] Started: jobId={}, chapterId={}",
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
            // Look up scene IDs from the database (available regardless of event context)
            List<Scene> scenes = sceneRepo.findByChapterId(chapterId);
            List<UUID> sceneIds = scenes.stream().map(Scene::getEventId).toList();

            // Stage 2: LLM rolling-triad co-reference pass — writes SAME_EVENT links
            EventCorefModels.CorefPassResult corefResult = eventCoreferenceService.runCorefPass(
                    sceneIds, chapterId, jobId);

            log.info("[CHAPTER_EVENT_RESOLUTION] Stage 2 complete: jobId={}, chapterId={}, windowsRun={}, linksCreated={}",
                    jobId, chapterId, corefResult.windowsRun(), corefResult.linksCreated());

            // Stage 3: deterministic aggregation from SAME_EVENT chains → ChapterEvent nodes
            ChapterEventResolutionResult aggregationResult =
                    chapterEventResolutionService.resolveChapter(chapterId);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_EVENT_RESOLUTION,
                    String.format("Coref: %d windows, %d links; Aggregation: %d events from %d mentions",
                            corefResult.windowsRun(), corefResult.linksCreated(),
                            aggregationResult.chapterEventsCreated(),
                            aggregationResult.rawMentionsProcessed()),
                    Map.of(
                            "windowsRun", corefResult.windowsRun(),
                            "linksCreated", corefResult.linksCreated(),
                            "rawMentionsProcessed", aggregationResult.rawMentionsProcessed(),
                            "chapterEventsCreated", aggregationResult.chapterEventsCreated(),
                            "failedCorefWindowCount", aggregationResult.failedCorefWindowCount()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHAPTER_EVENT_RESOLUTION] Failed for job={} chapter={}: {}",
                    jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.CHAPTER_EVENT_RESOLUTION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.CHAPTER_EVENT_RESOLUTION,
                            sanitizeMessage(e), elapsed);
        }
    }

    private boolean isRetryableError(Exception e) {
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("API") || message.contains("timeout") || message.contains("rate limit") || message.contains("connection"));
    }
}
