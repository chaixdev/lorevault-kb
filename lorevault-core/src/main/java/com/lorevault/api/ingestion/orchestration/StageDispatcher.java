package com.lorevault.api.ingestion.orchestration;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageOperation;
import com.lorevault.api.ingestion.pipeline.StageStatus;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.common.error.ExceptionSanitizer;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Centralized dispatcher that replaces the 15 individual handler
 * {@code @Async @EventListener onTrigger} methods with a single
 * event listener.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Executor routing: {@code SCENE_SEGMENTATION} → dedicated
 *       {@code sceneDetectionTaskExecutor}, all others →
 *       {@code ingestionLaneTaskExecutor}</li>
 *   <li>MDC context: sets {@code stage}, {@code jobId}, and {@code stageId}
 *       before execution so all downstream logs carry stage identity</li>
 *   <li>Guard: atomic TRIGGERED→RUNNING transition, returns stage ID</li>
 *   <li>Idempotency: checks {@code Stage.status == COMPLETED}</li>
 *   <li>Error boundary: catches unchecked exceptions and converts
 *       to {@link StepResult#failure}</li>
 *   <li>Completion: emits {@link StageCompletedEvent}</li>
 * </ol>
 *
 * <h3>Transaction boundary</h3>
 * This method must NOT be {@code @Transactional}. Each handler
 * manages its own transaction boundaries via its delegate services.
 * LLM calls may take 30–120s — holding a dispatcher-level
 * transaction across that duration would exhaust the Neo4j
 * connection pool.
 *
 * <h3>Startup validation</h3>
 * At construction time, the dispatcher verifies every
 * {@link StageKey} has exactly one {@link StageOperation} handler
 * registered. Missing or duplicate registrations produce a
 * fail-fast {@code IllegalStateException}.
 */
@Component
@Slf4j
public class StageDispatcher {

    private final Map<StageKey, StageOperation> handlers;
    private final StageGraphRepository stageRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecutor sceneDetectionTaskExecutor;
    private final TaskExecutor ingestionLaneTaskExecutor;

    @Autowired
    public StageDispatcher(
            List<StageOperation> handlerList,
            StageGraphRepository stageRepo,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("sceneDetectionTaskExecutor") TaskExecutor sceneDetectionTaskExecutor,
            @Qualifier("ingestionLaneTaskExecutor") TaskExecutor ingestionLaneTaskExecutor
    ) {
        this.stageRepo = stageRepo;
        this.eventPublisher = eventPublisher;
        this.sceneDetectionTaskExecutor = sceneDetectionTaskExecutor;
        this.ingestionLaneTaskExecutor = ingestionLaneTaskExecutor;

        this.handlers = new EnumMap<>(StageKey.class);
        for (StageOperation handler : handlerList) {
            ForStage anno = handler.getClass().getAnnotation(ForStage.class);
            if (anno == null) {
                log.warn("StageOperation bean '{}' is missing @ForStage annotation — skipping",
                        handler.getClass().getSimpleName());
                continue;
            }
            StageKey key = anno.value();
            if (handlers.containsKey(key)) {
                throw new IllegalStateException(
                        "Duplicate @ForStage(" + key + ") registration: "
                                + handlers.get(key).getClass().getSimpleName()
                                + " and " + handler.getClass().getSimpleName());
            }
            handlers.put(key, handler);
            log.info("Registered StageOperation: {} → {}", key, handler.getClass().getSimpleName());
        }

        // Fail-fast: every StageKey must have a handler
        for (StageKey key : StageKey.values()) {
            if (!handlers.containsKey(key)) {
                throw new IllegalStateException(
                        "No @ForStage handler registered for stage: " + key);
            }
        }
    }

    /**
     * Single event listener replacing 15 individual {@code @Async @EventListener} methods.
     *
     * <p>Note: NOT {@code @Async} — executor routing is handled programmatically
     * to preserve the dedicated {@code sceneDetectionTaskExecutor} for scene detection.
     */
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        executorFor(event.getStage()).execute(() -> dispatch(event));
    }

    private TaskExecutor executorFor(StageKey stage) {
        return stage == StageKey.SCENE_SEGMENTATION
                ? sceneDetectionTaskExecutor
                : ingestionLaneTaskExecutor;
    }

    private void dispatch(StageTriggeredEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();
        StageKey stage = event.getStage();

        MDC.put("stage", stage.name());
        MDC.put("jobId", jobId.toString());

        StageOperation handler = handlers.get(stage);
        if (handler == null) {
            log.error("No handler registered for stage: {} (this should not happen after startup validation)",
                    stage);
            MDC.clear();
            return;
        }

        // 1. Guard: atomic TRIGGERED → RUNNING, returns stage ID
        Optional<UUID> stageIdOpt = stageRepo.setRunningConditionally(jobId, stage);
        if (stageIdOpt.isEmpty()) {
            MDC.clear();
            return;
        }
        UUID stageId = stageIdOpt.get();
        MDC.put("stageId", stageId.toString());

        // 2. Idempotency: check Stage.status (not StageOutput)
        if (isAlreadyCompleted(jobId, stage)) {
            stageRepo.setSkipped(jobId, stage);
            emitComplete(jobId, chapterId, bookId, stage,
                    StepResult.success(stage, "Skipped — already completed", 0L));
            MDC.clear();
            return;
        }

        // 3. Execute with error boundary
        StageExecutionContext ctx = new StageExecutionContext(stageId, jobId, chapterId, bookId, stage);
        StepResult result;
        try {
            result = handler.execute(ctx);
        } catch (Exception e) {
            log.error("Unhandled exception in stage {}: jobId={}", stage, jobId, e);
            result = StepResult.failure(stage,
                    ExceptionSanitizer.sanitizeMessage(e), 0L);
        }

        // 4. Emit completion
        emitComplete(jobId, chapterId, bookId, stage, result);

        MDC.clear();
    }

    /**
     * Check if the stage is already COMPLETED for this job.
     * Replaces the old StageOutput-based idempotency check.
     */
    private boolean isAlreadyCompleted(UUID jobId, StageKey stage) {
        return stageRepo.findByJobIdAndStep(jobId, stage)
                .map(s -> s.getStatus() == StageStatus.COMPLETED)
                .orElse(false);
    }

    private void emitComplete(UUID jobId, UUID chapterId, UUID bookId,
                               StageKey stage, StepResult result) {
        eventPublisher.publishEvent(new StageCompletedEvent(
                this, jobId, chapterId, bookId, stage, result));
    }

    /**
     * Test constructor — bypasses annotation scanning and startup validation.
     * Accepts a pre-built handler map for direct injection in tests.
     */
    StageDispatcher(
            Map<StageKey, StageOperation> handlers,
            StageGraphRepository stageRepo,
            ApplicationEventPublisher eventPublisher,
            TaskExecutor sceneDetectionTaskExecutor,
            TaskExecutor ingestionLaneTaskExecutor
    ) {
        this.handlers = new EnumMap<>(handlers);
        this.stageRepo = stageRepo;
        this.eventPublisher = eventPublisher;
        this.sceneDetectionTaskExecutor = sceneDetectionTaskExecutor;
        this.ingestionLaneTaskExecutor = ingestionLaneTaskExecutor;
    }
}
