package com.lorevault.api.service;

import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.model.Chapter;
import com.lorevault.api.model.IngestionJob;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.IngestionJobRepository;
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

    private final ChapterRepository chapterRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionService ingestionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("ingestionTaskExecutor")
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        log.info("Processing chapter ingestion event for job {} and chapter {} on thread {}", 
                event.getJobId(), event.getChapterId(), Thread.currentThread().getName());

        try {
            // Defensive retry logic for potential race conditions
            IngestionJob job = findJobWithRetry(event.getJobId());
            Chapter chapter = findChapterWithRetry(event.getChapterId());

            // Call the existing processing logic
            ingestionService.processChapter(job, chapter);

        } catch (Exception e) {
            log.error("Error processing chapter ingestion event for job {} and chapter {}", 
                    event.getJobId(), event.getChapterId(), e);
        }
    }

    /**
     * Find job with retry logic to handle potential race conditions
     */
    private IngestionJob findJobWithRetry(UUID jobId) {
        int maxAttempts = 3;
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<IngestionJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isPresent()) {
                log.debug("Found job {} on attempt {}", jobId, attempt);
                return jobOpt.get();
            }

            if (attempt < maxAttempts) {
                log.warn("Job {} not found on attempt {}/{}, retrying in {}ms", 
                        jobId, attempt, maxAttempts, delayMs);
                try {
                    Thread.sleep(delayMs);
                    delayMs *= 2; // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for job", ie);
                }
            }
        }

        throw new IllegalStateException("Job not found after " + maxAttempts + " attempts: " + jobId);
    }

    /**
     * Find chapter with retry logic to handle potential race conditions.
     * Eagerly loads scenes to avoid LazyInitializationException in async context.
     */
    private Chapter findChapterWithRetry(UUID chapterId) {
        int maxAttempts = 3;
        long delayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<Chapter> chapterOpt = chapterRepository.findByIdWithScenes(chapterId);
            if (chapterOpt.isPresent()) {
                log.debug("Found chapter {} with scenes on attempt {}", chapterId, attempt);
                return chapterOpt.get();
            }

            if (attempt < maxAttempts) {
                log.warn("Chapter {} not found on attempt {}/{}, retrying in {}ms", 
                        chapterId, attempt, maxAttempts, delayMs);
                try {
                    Thread.sleep(delayMs);
                    delayMs *= 2; // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for chapter", ie);
                }
            }
        }

        throw new IllegalStateException("Chapter not found after " + maxAttempts + " attempts: " + chapterId);
    }
}
