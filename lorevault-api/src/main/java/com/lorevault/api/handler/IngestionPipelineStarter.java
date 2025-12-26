package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.event.ingestion.ChapterPersistedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Handler that bridges the legacy ChapterIngestionEvent to the new event-driven pipeline.
 * 
 * Listens to: ChapterIngestionEvent (the existing event published by IngestionService)
 * Emits: ChapterPersistedEvent (starts the new pipeline)
 * 
 * This handler exists as a bridge during the refactoring process. Once the old
 * synchronous processChapter() method is removed, this handler ensures the 
 * event-driven pipeline is triggered.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionPipelineStarter {

    private final ContentPersistencePort contentPersistencePort;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        
        log.info("[PIPELINE_START] Received ChapterIngestionEvent: job={}, chapter={}", 
                jobId, chapterId);
        
        try {
            // Look up the chapter to get the bookId
            Chapter chapter = contentPersistencePort.findChapterById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            
            UUID bookId = chapter.getBookId();
            
            log.info("[PIPELINE_START] Starting event-driven pipeline: job={}, chapter={}, book={}", 
                    jobId, chapterId, bookId);
            
            // Emit the first event in the new pipeline
            eventPublisher.publishEvent(new ChapterPersistedEvent(this, jobId, chapterId, bookId));
            
        } catch (Exception e) {
            log.error("[PIPELINE_START] Failed to start pipeline for job={}, chapter={}: {}", 
                    jobId, chapterId, e.getMessage(), e);
            // The old synchronous flow will still handle this as a fallback
        }
    }
}
