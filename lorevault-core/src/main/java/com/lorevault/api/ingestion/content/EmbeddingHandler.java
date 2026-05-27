package com.lorevault.api.ingestion.content;

import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;

import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.ai.embedding.EmbeddingService;
import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.orchestration.StageOutputGraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.UUID;

/**
 * Handler for embedding generation stage of the ingestion pipeline.
 *
 * Listens to: StageTriggeredEvent (EMBEDDING)
 * Emits: StageCompletedEvent (on success, skip, or failure)
 *
 * Implements {@link EmbeddingOperation} so the step-by-step execution controller or step-execution
 * endpoints can invoke embedding generation directly without Spring event dispatch.
 *
 * Responsibilities:
 * - Generate vector embeddings for all chunks in the chapter
 * - Store embeddings in the database for semantic search
 */
@Component
@Slf4j
public class EmbeddingHandler implements EmbeddingOperation {

    private final ChunkGraphRepository chunkRepo;
    private final EmbeddingService embeddingService;
    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;
    private final StageOutputGraphRepository stageOutputRepo;

    public EmbeddingHandler(
            ChunkGraphRepository chunkRepo,
            EmbeddingService embeddingService,
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chunkRepo = chunkRepo;
        this.embeddingService = embeddingService;
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
        this.eventPublisher = eventPublisher;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        // 0. Stage key guard: reject events for other stages
        if (event.getStage() != StageKey.EMBEDDING) return;

        // 1. Guard: only one thread executes at a time
        if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) {
            return; // already RUNNING or no longer TRIGGERED
        }

        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();

        // 2. Idempotency: does StageOutput already exist?
        if (stageOutputRepo.existsByChapterIdAndStep(chapterId, event.getStage())) {
            stageRepo.setSkipped(jobId, event.getStage());
            eventPublisher.publishEvent(new StageCompletedEvent(
                    this, jobId, chapterId, event.getStage(),
                    StepResult.success(event.getStage(),
                            "Skipped — already completed", 0L)));
            log.info("[EMBEDDING] Skipped — StageOutput already exists for chapter {}", chapterId);
            return;
        }

        log.info("[LANE:CONTENT] [EMBEDDING] Starting for job={}, chapter={}", jobId, chapterId);

        // 3. Do the work (existing execute method)
        StepResult result = execute(jobId, chapterId);

        // 4. Emit completion — coordinator handles DAG transitions
        eventPublisher.publishEvent(new StageCompletedEvent(
                this, jobId, chapterId, event.getStage(), result));
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        try {
            // Idempotency: skip if embeddings already exist for this chapter
            int existingEmbeddings = chunkRepo.countEmbeddingsByChapterId(chapterId);
            if (existingEmbeddings > 0) {
                log.info("[EMBEDDING] Skipping — {} embeddings already exist for chapter {}",
                        existingEmbeddings, chapterId);
                long elapsed = System.currentTimeMillis() - start;
                return StepResult.success(StageKey.EMBEDDING,
                        String.format("Skipped — %d embeddings already exist", existingEmbeddings),
                        Map.of("embeddingsGenerated", existingEmbeddings),
                        elapsed);
            }

            // Generate embeddings for all chunks in the chapter
            int embeddedCount = embeddingService.generateEmbeddingsForChapter(chapterId);

            log.info("[EMBEDDING] Completed for chapter {}: {} embeddings generated",
                    chapterId, embeddedCount);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.EMBEDDING,
                    String.format("Generated embeddings for %d chunks", embeddedCount),
                    Map.of("embeddingsGenerated", embeddedCount),
                    elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[EMBEDDING] Failed for job={} chapter={}: {}", jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure(StageKey.EMBEDDING,
                            sanitizeMessage(e), elapsed)
                    : StepResult.failure(StageKey.EMBEDDING,
                            sanitizeMessage(e), elapsed);
        }
    }

    private boolean isRetryableError(Exception e) {
        if (e instanceof EmbeddingGenerationException embeddingGenerationException
                && embeddingGenerationException.failure() != null) {
            return "EMBEDDING_BACKEND_UNAVAILABLE".equals(embeddingGenerationException.failure().code());
        }
        String message = e.getMessage();
        return message != null && (
                message.contains("API") ||
                message.contains("timeout") ||
                message.contains("rate limit") ||
                message.contains("connection"));
    }
}
