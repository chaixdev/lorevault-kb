package com.lorevault.api.service.ingestion;

import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import com.lorevault.api.application.port.ContentPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles chapter processing events asynchronously after transaction commit
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChapterProcessor {

    private final ContentPersistencePort contentPersistencePort;
    private final IngestionService ingestionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("ingestionTaskExecutor")
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        log.info("Processing chapter ingestion event for job {} and chapter {} on thread {}", 
                event.getJobId(), event.getChapterId(), Thread.currentThread().getName());

        try {
            IngestionJobNode jobNode = findJobWithRetry(event.getJobId());
            ChapterNode chapterNode = findChapterWithRetry(event.getChapterId());

            // Minimal transient domain objects for processing (rawText & scenes still domain-gap; future refactor)
            IngestionJob job = new IngestionJob();
            job.setId(jobNode.getId());
            job.setChapterId(jobNode.getChapterId());
            job.setCurrentStatus(jobNode.getCurrentStatus());
            job.setProgressPercent(jobNode.getProgressPercent());
            job.setCreatedAt(jobNode.getCreatedAt());
            job.setCompletedAt(jobNode.getCompletedAt());

            // Build transient Chapter
            Chapter chapter = new Chapter();
            chapter.setId(chapterNode.getId());
            chapter.setRawText(chapterNode.getRawText());

            // Scenes will be resolved later when scene graph refactor adds retrieval
            ingestionService.processChapter(job, chapter);

        } catch (Exception e) {
            log.error("Error processing chapter ingestion event for job {} and chapter {}", 
                    event.getJobId(), event.getChapterId(), e);
        }
    }

    private IngestionJobNode findJobWithRetry(UUID jobId) {
        int maxAttempts = 3; 
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<IngestionJobNode> jobOpt = contentPersistencePort.findJob(jobId);
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

    private ChapterNode findChapterWithRetry(UUID chapterId) {
        int maxAttempts = 3; 
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<ChapterNode> chOpt = contentPersistencePort.findChapterById(chapterId);
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
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException("Interrupted", ie); }
    }
}
