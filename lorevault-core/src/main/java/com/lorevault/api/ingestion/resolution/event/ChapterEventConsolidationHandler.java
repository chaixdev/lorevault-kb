package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventCorefModels;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;

import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.scene.SceneGraphRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Async handler that orchestrates Stage 2 (LLM co-reference) and Stage 3 (ChapterEvent aggregation)
 * in response to a {@link StageTriggeredEvent}.
 *
 * <p>Implements {@link ChapterEventConsolidationOperation} so the step-by-step execution controller or step-execution
 * endpoints can invoke event resolution directly without Spring event dispatch.
 *
 * <p>Uses stage orchestration graph for conditional execution, idempotency, and
 * completion signalling via {@link StageCompletedEvent}.
 */
@Component
@Slf4j
@ForStage(StageKey.CHAPTER_EVENT_CONSOLIDATION)
public class ChapterEventConsolidationHandler implements ChapterEventConsolidationOperation {

    private final EventCoreferenceService eventCoreferenceService;
    private final ChapterEventConsolidationService chapterEventConsolidationService;
    private final SceneGraphRepository sceneRepo;
    private final ChapterGraphRepository chapterRepo;

    public ChapterEventConsolidationHandler(
            EventCoreferenceService eventCoreferenceService,
            ChapterEventConsolidationService chapterEventConsolidationService,
            SceneGraphRepository sceneRepo,
            ChapterGraphRepository chapterRepo
    ) {
        this.eventCoreferenceService = eventCoreferenceService;
        this.chapterEventConsolidationService = chapterEventConsolidationService;
        this.sceneRepo = sceneRepo;
        this.chapterRepo = chapterRepo;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            // Look up scene IDs from the database (available regardless of event context)
            List<Scene> scenes = sceneRepo.findByChapterId(chapterId);
            List<UUID> sceneIds = scenes.stream().map(Scene::getEventId).toList();

            // Stage 2: LLM rolling-triad co-reference pass — writes SAME_EVENT links
            EventCorefModels.CorefPassResult corefResult = eventCoreferenceService.runCorefPass(
                    sceneIds, chapterId, jobId);

            log.info("[CHAPTER_EVENT_CONSOLIDATION] Stage 2 complete: jobId={}, chapterId={}, windowsRun={}, linksCreated={}",
                    jobId, chapterId, corefResult.windowsRun(), corefResult.linksCreated());

            // Stage 3: deterministic aggregation from SAME_EVENT chains → ChapterEvent nodes
            ChapterEventConsolidationResult aggregationResult =
                    chapterEventConsolidationService.consolidateChapter(ctx, chapterId);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHAPTER_EVENT_CONSOLIDATION,
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
            log.error("[CHAPTER_EVENT_CONSOLIDATION] Failed for job={} chapter={}: {}",
                    jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.CHAPTER_EVENT_CONSOLIDATION,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.CHAPTER_EVENT_CONSOLIDATION,
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
