package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventCorefModels;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.scene.SceneGraphRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async handler that orchestrates Stage 2 (LLM co-reference) and Stage 3 (ChapterEvent aggregation)
 * in response to a {@link ScenesDetectedEvent}.
 *
 * <p>Implements {@link ChapterEventResolutionOperation} so the CLI module or step-execution
 * endpoints can invoke event resolution directly without Spring event dispatch.
 *
 * <p>Uses {@link PipelineStageSupport} for durable failure emission — any exception from either
 * stage emits {@code IngestionFailedEvent} and marks the job FAILED, rather than silently swallowing
 * or rethrowing to the executor.</p>
 *
 * <p>On success, publishes {@link ChapterEventsResolvedEvent} so the
 * {@code IngestionCompletionCoordinator} fan-in can unblock.</p>
 */
@Component
@Slf4j
public class ChapterEventResolutionHandler implements ChapterEventResolutionOperation {

    static final String STAGE_EVENT_COREF = "EVENT_COREF";
    static final String STAGE_CHAPTER_EVENT_AGGREGATION = "CHAPTER_EVENT_AGGREGATION";
    static final String STAGE_EVENT_RESOLUTION_PUBLISH = "EVENT_RESOLUTION_PUBLISH";

    private final EventCoreferenceService eventCoreferenceService;
    private final ChapterEventResolutionService chapterEventResolutionService;
    private final SceneGraphRepository sceneRepo;
    private final ChapterGraphRepository chapterRepo;
    private final PipelineStageSupport pipelineStageSupport;
    private final ApplicationEventPublisher eventPublisher;

    public ChapterEventResolutionHandler(
            EventCoreferenceService eventCoreferenceService,
            ChapterEventResolutionService chapterEventResolutionService,
            SceneGraphRepository sceneRepo,
            ChapterGraphRepository chapterRepo,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.eventCoreferenceService = eventCoreferenceService;
        this.chapterEventResolutionService = chapterEventResolutionService;
        this.sceneRepo = sceneRepo;
        this.chapterRepo = chapterRepo;
        this.pipelineStageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
        this.eventPublisher = eventPublisher;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID chapterId = event.getChapterId();
        UUID jobId = event.getJobId();
        UUID correlationId = event.getCorrelationId();
        UUID bookId = event.getBookId();

        log.info("[LANE:EVENT] [CHAPTER_EVENT_RESOLUTION] Started: jobId={}, correlationId={}, chapterId={}, bookId={}",
                jobId, correlationId, chapterId, bookId);

        StepResult result = execute(jobId, chapterId);

        if (result.success()) {
            try {
                Chapter chapter = chapterRepo.findById(chapterId).orElse(null);
                UUID resolvedBookId = chapter != null ? chapter.getBookId() : null;

                log.info(
                        "[LANE:EVENT] [CHAPTER_EVENT_RESOLUTION] Completed: jobId={}, chapterId={}, bookId={}, mentionCount={}, chapterEventCount={}, failedCorefWindowCount={}",
                        jobId, chapterId, resolvedBookId,
                        result.counts().getOrDefault("rawMentionsProcessed", 0),
                        result.counts().getOrDefault("chapterEventsCreated", 0),
                        result.counts().getOrDefault("failedCorefWindowCount", 0)
                );

                eventPublisher.publishEvent(new ChapterEventsResolvedEvent(
                        this,
                        jobId,
                        correlationId,
                        chapterId,
                        resolvedBookId,
                        result.counts().getOrDefault("rawMentionsProcessed", 0) > 0,
                        result.counts().getOrDefault("rawMentionsProcessed", 0),
                        result.counts().getOrDefault("chapterEventsCreated", 0),
                        result.counts().getOrDefault("failedCorefWindowCount", 0)
                ));
            } catch (Exception e) {
                log.error("[CHAPTER_EVENT_RESOLUTION] Failed to publish success event for job={}, chapter={}: {}",
                        jobId, chapterId, e.getMessage(), e);
                pipelineStageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                        "CHAPTER_EVENT_RESOLUTION failed to publish: " + e.getMessage());
            }
        } else {
            log.warn(
                    "[LANE:EVENT] [CHAPTER_EVENT_RESOLUTION] Failed: jobId={}, correlationId={}, chapterId={}, reason={}",
                    jobId, correlationId, chapterId, result.summary()
            );
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, correlationId, chapterId, "CHAPTER_EVENT_RESOLUTION",
                    result.summary(), result.retryable()));
            pipelineStageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    "CHAPTER_EVENT_RESOLUTION failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        try {
            // Look up scene IDs from the database (available regardless of event context)
            List<Scene> scenes = sceneRepo.findByChapterId(chapterId);
            List<UUID> sceneIds = scenes.stream().map(Scene::getEventId).toList();

            pipelineStageSupport.updateJobStatus(
                    jobId,
                    IngestionStatus.EVENT_COREF,
                    "Resolving cross-scene event co-reference",
                    Map.of(
                            "chapterId", chapterId.toString(),
                            "sceneCount", sceneIds.size()
                    )
            );

            // Stage 2: LLM rolling-triad co-reference pass — writes SAME_EVENT links
            EventCorefModels.CorefPassResult corefResult = eventCoreferenceService.runCorefPass(
                    sceneIds, chapterId, jobId);

            log.info("[CHAPTER_EVENT_RESOLUTION] Stage 2 complete: jobId={}, chapterId={}, windowsRun={}, linksCreated={}",
                    jobId, chapterId, corefResult.windowsRun(), corefResult.linksCreated());

            pipelineStageSupport.updateJobStatus(
                    jobId,
                    IngestionStatus.CHAPTER_EVENT_AGGREGATION,
                    "Aggregating chapter-level events from co-reference chains",
                    Map.of(
                            "chapterId", chapterId.toString(),
                            "windowsRun", corefResult.windowsRun(),
                            "linksCreated", corefResult.linksCreated(),
                            "failedCorefWindowCount", corefResult.failedWindowCount()
                    )
            );

            // Stage 3: deterministic aggregation from SAME_EVENT chains → ChapterEvent nodes
            ChapterEventResolutionResult aggregationResult =
                    chapterEventResolutionService.resolveChapter(chapterId);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success("CHAPTER_EVENT_RESOLUTION",
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
                    ? StepResult.retryableFailure("CHAPTER_EVENT_RESOLUTION",
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure("CHAPTER_EVENT_RESOLUTION",
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
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
