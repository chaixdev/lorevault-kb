package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.event.ingestion.EmbeddingsGeneratedEvent;
import com.lorevault.api.event.ingestion.IngestionCompletedEvent;
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

import java.util.UUID;

/**
 * Handler for completing the ingestion pipeline.
 * 
 * Listens to: EmbeddingsGeneratedEvent
 * Emits: IngestionCompletedEvent
 * 
 * Responsibilities:
 * - Mark the ingestion job as completed
 * - Gather final statistics
 * - Emit completion event for any downstream consumers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompletionHandler {

    private final ContentPersistencePort contentPersistencePort;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleEmbeddingsGenerated(EmbeddingsGeneratedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        
        log.info("[COMPLETION] Processing for job={}, chapter={}", jobId, chapterId);
        
        try {
            // Gather statistics
            int sceneCount = contentPersistencePort.findScenesByChapterId(chapterId).size();
            int chunkCount = contentPersistencePort.countChunksByChapterId(chapterId);
            int embeddedCount = event.getEmbeddedChunkCount();
            
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
}
