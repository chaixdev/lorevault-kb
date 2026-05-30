package com.lorevault.api.ai.embedding;

import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StepResult;

import com.lorevault.api.library.chunk.ChunkGraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
@ForStage(StageKey.EMBEDDING)
public class EmbeddingHandler implements EmbeddingOperation {

    private final ChunkGraphRepository chunkRepo;
    private final EmbeddingService embeddingService;

    public EmbeddingHandler(
            ChunkGraphRepository chunkRepo,
            EmbeddingService embeddingService
    ) {
        this.chunkRepo = chunkRepo;
        this.embeddingService = embeddingService;
    }

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
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
