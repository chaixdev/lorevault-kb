package com.lorevault.api.handler;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.event.ingestion.ChunksCreatedEvent;
import com.lorevault.api.event.ingestion.IngestionCompletedEvent;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChunkGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.IngestionJobGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SceneGraphRepository;
import com.lorevault.api.service.content.EmbeddingService;
import com.lorevault.api.service.ingestion.IngestionJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Handler for embedding generation and completion stage of the ingestion pipeline.
 * 
 * Listens to: ChunksCreatedEvent
 * Emits: IngestionCompletedEvent (on success) or IngestionFailedEvent (on failure)
 * 
 * Responsibilities:
 * - Generate vector embeddings for all chunks in the chapter
 * - Store embeddings in the database for semantic search
 * - Gather final statistics and mark job complete
 */
@Component
@Slf4j
public class EmbeddingHandler {

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

    @Async
    @EventListener
    public void handleChunksCreated(ChunksCreatedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        
        log.info("[EMBEDDING] Starting for job={}, chapter={}, chunkCount={}", 
                jobId, chapterId, event.getChunkCount());
        
                stageSupport.runStage(
                        this,
                                "EMBEDDING",
                                jobId,
                                chapterId,
                                () -> {
                        stageSupport.updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    "Generating vector embeddings for semantic search");

            // Generate embeddings for all chunks in the chapter
            int embeddedCount = embeddingService.generateEmbeddingsForChapter(chapterId);
            
            stageSupport.updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    String.format("Generated embeddings for %d chunks", embeddedCount));
            
            log.info("[EMBEDDING] Completed for chapter {}: {} embeddings generated", 
                    chapterId, embeddedCount);
            
            // Complete the ingestion job (merged from CompletionHandler)
            completeIngestion(jobId, chapterId, embeddedCount);

                        return null;
                                },
                                this::isRetryableError
                );
    }

    private void completeIngestion(UUID jobId, UUID chapterId, int embeddedCount) {
        log.info("[COMPLETION] Processing for job={}, chapter={}", jobId, chapterId);
        
        try {
            // Gather statistics
            int sceneCount = sceneRepo.findByChapterId(chapterId).size();
            int via = chunkRepo.countByChapterIdViaScenes(chapterId);
            int chunkCount = via > 0 ? via : chunkRepo.countByChapterId(chapterId);
            
            // Get chapter length for job completion
            Chapter chapter = chapterRepo.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            int chapterLength = chapter.getRawText() != null ? chapter.getRawText().length() : 0;
            
            // Get the job and mark complete
            var job = jobRepo.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
            
            ingestionJobService.completeJob(job, chapterId, chapterLength);
            
            log.info("[COMPLETION] Job {} completed: {} scenes, {} chunks, {} embeddings", 
                    jobId, sceneCount, chunkCount, embeddedCount);
            
            // Emit completion event
            eventPublisher.publishEvent(new IngestionCompletedEvent(
                    this, jobId, chapterId, sceneCount, chunkCount, embeddedCount));
            
        } catch (Exception e) {
            log.error("[COMPLETION] Failed for job={}, chapter={}: {}", 
                    jobId, chapterId, e.getMessage(), e);
            // Don't emit failure - the work is done, just completion tracking failed
        }
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("API") || 
                message.contains("timeout") ||
                message.contains("rate limit") ||
                message.contains("connection"));
    }
}
