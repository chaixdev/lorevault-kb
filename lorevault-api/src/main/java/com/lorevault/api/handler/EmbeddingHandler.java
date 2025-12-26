package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.event.ingestion.ChunksCreatedEvent;
import com.lorevault.api.event.ingestion.IngestionCompletedEvent;
import com.lorevault.api.event.ingestion.IngestionFailedEvent;
import com.lorevault.api.service.content.EmbeddingService;
import com.lorevault.api.service.ingestion.IngestionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collections;
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
@RequiredArgsConstructor
@Slf4j
public class EmbeddingHandler {

    private final ContentPersistencePort contentPersistencePort;
    private final EmbeddingService embeddingService;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChunksCreated(ChunksCreatedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();
        
        log.info("[EMBEDDING] Starting for job={}, chapter={}, chunkCount={}", 
                jobId, chapterId, event.getChunkCount());
        
        try {
            updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS, 
                    "Generating vector embeddings for semantic search");

            // Generate embeddings for all chunks in the chapter
            int embeddedCount = embeddingService.generateEmbeddingsForChapter(chapterId);
            
            updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    String.format("Generated embeddings for %d chunks", embeddedCount));
            
            log.info("[EMBEDDING] Completed for chapter {}: {} embeddings generated", 
                    chapterId, embeddedCount);
            
            // Complete the ingestion job (merged from CompletionHandler)
            completeIngestion(jobId, chapterId, embeddedCount);
            
        } catch (Exception e) {
            log.error("[EMBEDDING] Failed for job={}, chapter={}: {}", 
                    jobId, chapterId, e.getMessage(), e);
            emitFailure(jobId, chapterId, "EMBEDDING", e);
        }
    }

    private void completeIngestion(UUID jobId, UUID chapterId, int embeddedCount) {
        log.info("[COMPLETION] Processing for job={}, chapter={}", jobId, chapterId);
        
        try {
            // Gather statistics
            int sceneCount = contentPersistencePort.findScenesByChapterId(chapterId).size();
            int chunkCount = contentPersistencePort.countChunksByChapterId(chapterId);
            
            // Get chapter length for job completion
            Chapter chapter = contentPersistencePort.findChapterById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            int chapterLength = chapter.getRawText() != null ? chapter.getRawText().length() : 0;
            
            // Get the job and mark complete
            var job = contentPersistencePort.findJob(jobId)
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

    private void emitFailure(UUID jobId, UUID chapterId, String stage, Exception e) {
        boolean retryable = isRetryableError(e);
        
        eventPublisher.publishEvent(new IngestionFailedEvent(
                this, jobId, chapterId, stage, e.getMessage(), retryable));
        
        ingestionJobService.updateJobStatus(jobId, IngestionStatus.FAILED, 
                stage + " failed: " + e.getMessage(), Collections.emptyMap());
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("API") || 
                message.contains("timeout") ||
                message.contains("rate limit") ||
                message.contains("connection"));
    }

    private void updateJobStatus(UUID jobId, IngestionStatus status, String description) {
        ingestionJobService.updateJobStatus(jobId, status, description, Collections.emptyMap());
    }
}
