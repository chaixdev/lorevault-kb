package com.lorevault.api.ingestion;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.ingestion.IngestionStatus;
import com.lorevault.api.ingestion.ChunksCreatedEvent;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.ingestion.IngestionJobGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ai.EmbeddingService;
import com.lorevault.api.ingestion.IngestionJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
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
public class EmbeddingHandler {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingHandler.class);

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
        BeanWrapperImpl eventBean = new BeanWrapperImpl(event);
        UUID jobId = (UUID) eventBean.getPropertyValue("jobId");
        UUID chapterId = (UUID) eventBean.getPropertyValue("chapterId");
        Integer chunkCount = (Integer) eventBean.getPropertyValue("chunkCount");
        
        log.info("[EMBEDDING] Starting for job={}, chapter={}, chunkCount={}", 
                jobId, chapterId, chunkCount);
        
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
            String rawText = (String) new BeanWrapperImpl(chapter).getPropertyValue("rawText");
            int chapterLength = rawText != null ? rawText.length() : 0;
            
            log.info("[COMPLETION] Embedding branch finished for job {}: {} scenes, {} chunks, {} embeddings", 
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
