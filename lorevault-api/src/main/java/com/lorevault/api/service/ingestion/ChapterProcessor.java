package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.event.ChapterIngestionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles chapter processing events asynchronously after transaction commit.
 * 
 * DISABLED: Event listener disabled during event-driven pipeline refactor.
 * The new IngestionPipelineStarter → SceneDetectionHandler → ChunkingHandler → 
 * EmbeddingHandler → CompletionHandler pipeline now handles chapter processing.
 * 
 * This class is kept temporarily for reference and will be removed once the 
 * new pipeline is fully validated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChapterProcessor {

    private final ContentPersistencePort contentPersistencePort;
    private final IngestionService ingestionService;

    // DISABLED: Old synchronous flow - replaced by event-driven handlers
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // @Async("ingestionTaskExecutor")
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        log.info("Processing chapter ingestion event for job {} and chapter {} on thread {}",
                event.getJobId(), event.getChapterId(), Thread.currentThread().getName());

        try {
            IngestionJob job = findJobWithRetry(event.getJobId());
            Chapter chapter = findChapterWithRetry(event.getChapterId());

            // Scenes will be resolved later when scene graph refactor adds retrieval
            ingestionService.processChapter(job, chapter);

        } catch (Exception e) {
            log.error("Error processing chapter ingestion event for job {} and chapter {}",
                    event.getJobId(), event.getChapterId(), e);
        }
    }



    private IngestionJob findJobWithRetry(UUID jobId) {
        int maxAttempts = 3;
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<IngestionJob> jobOpt = contentPersistencePort.findJob(jobId);
            if (jobOpt.isPresent()) {
                log.debug("Found job {} on attempt {}", jobId, attempt);
                return jobOpt.get();
            }

            if (attempt < maxAttempts) {
                sleep(delayMs);
                delayMs *= 2; // Exponential backoff
            }
        }
    throw new IllegalStateException("Job not found after retries: " + jobId);
    }

    private Chapter findChapterWithRetry(UUID chapterId) {
        int maxAttempts = 3;
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        Optional<Chapter> chOpt = contentPersistencePort.findChapterById(chapterId);
            if (chOpt.isPresent()) {
                log.debug("Found chapter {} on attempt {}", chapterId, attempt);
                return chOpt.get();
            }

            if (attempt < maxAttempts) {
                sleep(delayMs);
                delayMs *= 2; // Exponential backoff
            }
        }
    throw new IllegalStateException("Chapter not found after retries: " + chapterId);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", ie);
        }
    }
}
