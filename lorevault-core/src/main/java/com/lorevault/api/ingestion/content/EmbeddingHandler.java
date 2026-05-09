package com.lorevault.api.ingestion.content;

import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import com.lorevault.api.ingestion.pipeline.PipelineStageSupport;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.ingestion.events.ChunksCreatedEvent;
import com.lorevault.api.ingestion.events.EmbeddingsCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.ingestion.job.IngestionJobGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ai.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handler for embedding generation and completion stage of the ingestion pipeline.
 * 
 * Listens to: ChunksCreatedEvent
 * Emits: EmbeddingsCompletedEvent (on success) or IngestionFailedEvent (on failure)
 * 
 * Implements {@link EmbeddingOperation} so the CLI module or step-execution
 * endpoints can invoke embedding generation directly without Spring event dispatch.
 * 
 * Responsibilities:
 * - Generate vector embeddings for all chunks in the chapter
 * - Store embeddings in the database for semantic search
 * - Gather final statistics and mark job complete
 */
@Component
@Slf4j
public class EmbeddingHandler implements EmbeddingOperation {

    private final ChapterGraphRepository chapterRepo;
    private final ChunkGraphRepository chunkRepo;
    private final SceneGraphRepository sceneRepo;
    private final EmbeddingService embeddingService;
    private final IngestionJobService ingestionJobService;
    private final IngestionJobGraphRepository jobRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public EmbeddingHandler(
            ChapterGraphRepository chapterRepo,
            ChunkGraphRepository chunkRepo,
            SceneGraphRepository sceneRepo,
            EmbeddingService embeddingService,
            IngestionJobService ingestionJobService,
            IngestionJobGraphRepository jobRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterRepo = chapterRepo;
        this.chunkRepo = chunkRepo;
        this.sceneRepo = sceneRepo;
        this.embeddingService = embeddingService;
        this.ingestionJobService = ingestionJobService;
        this.jobRepo = jobRepo;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void handleChunksCreated(ChunksCreatedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();

        log.info("[LANE:CONTENT] [EMBEDDING] Starting for job={}, chapter={}, chunkCount={}",
                jobId, chapterId, event.getChunkCount());

        StepResult result = execute(jobId, chapterId);

        if (result.success()) {
            try {
                int embeddedCount = result.counts().getOrDefault("embeddingsGenerated", 0);
                completeIngestion(jobId, chapterId, embeddedCount);
            } catch (Exception e) {
                log.error("[EMBEDDING] Completion tracking failed for job={} chapter={}: {}", jobId, chapterId, e.getMessage(), e);
                eventPublisher.publishEvent(new IngestionFailedEvent(
                        this, jobId, chapterId, "EMBEDDING", e.getMessage(), false));
                stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                        "EMBEDDING failed: " + e.getMessage());
            }
        } else {
            eventPublisher.publishEvent(new IngestionFailedEvent(
                    this, jobId, chapterId, "EMBEDDING", result.summary(), result.retryable()));
            stageSupport.updateJobStatus(jobId, IngestionStatus.FAILED,
                    "EMBEDDING failed: " + result.summary());
        }
    }

    @Override
    public StepResult execute(UUID jobId, UUID chapterId) {
        long start = System.currentTimeMillis();
        try {
            stageSupport.updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    "Generating vector embeddings for semantic search");

            // Idempotency: skip if embeddings already exist for this chapter
            int existingEmbeddings = chunkRepo.countEmbeddingsByChapterId(chapterId);
            if (existingEmbeddings > 0) {
                log.info("[EMBEDDING] Skipping — {} embeddings already exist for chapter {}",
                        existingEmbeddings, chapterId);
                long elapsed = System.currentTimeMillis() - start;
                return StepResult.success("EMBEDDING",
                        String.format("Skipped — %d embeddings already exist", existingEmbeddings),
                        Map.of("embeddingsGenerated", existingEmbeddings),
                        elapsed);
            }

            // Generate embeddings for all chunks in the chapter
            int embeddedCount = embeddingService.generateEmbeddingsForChapter(chapterId);

            stageSupport.updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    String.format("Generated embeddings for %d chunks", embeddedCount));

            log.info("[EMBEDDING] Completed for chapter {}: {} embeddings generated",
                    chapterId, embeddedCount);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success("EMBEDDING",
                    String.format("Generated embeddings for %d chunks", embeddedCount),
                    Map.of("embeddingsGenerated", embeddedCount),
                    elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[EMBEDDING] Failed for job={} chapter={}: {}", jobId, chapterId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StepResult.retryableFailure("EMBEDDING",
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed)
                    : StepResult.failure("EMBEDDING",
                            PipelineStageSupport.sanitizeExceptionMessage(e), elapsed);
        }
    }

    private void completeIngestion(UUID jobId, UUID chapterId, int embeddedCount) {
        log.info("[LANE:CONTENT] [EMBEDDING_COMPLETION_BRANCH] Processing for job={}, chapter={}", jobId, chapterId);

        // Gather statistics
        int sceneCount = sceneRepo.findByChapterId(chapterId).size();
        int via = chunkRepo.countByChapterIdViaScenes(chapterId);
        int chunkCount = via > 0 ? via : chunkRepo.countByChapterId(chapterId);

        // Get chapter length for job completion
        Chapter chapter = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        String rawText = chapter.getRawText();
        int chapterLength = rawText != null ? rawText.length() : 0;

        log.info("[LANE:CONTENT] [EMBEDDING_COMPLETION_BRANCH] Embedding branch finished for job {}: {} scenes, {} chunks, {} embeddings",
                jobId, sceneCount, chunkCount, embeddedCount);

        eventPublisher.publishEvent(new EmbeddingsCompletedEvent(
                this,
                jobId,
                chapterId,
                sceneCount,
                chunkCount,
                embeddedCount,
                chapterLength
        ));
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
