package com.lorevault.api.service;

import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.model.Chapter;
import com.lorevault.api.model.IngestionJob;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles chapter processing events asynchronously
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChapterProcessor {

    private final ChapterRepository chapterRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionService ingestionService;

    @EventListener
    public void handleChapterIngestion(ChapterIngestionEvent event) {
        log.info("Processing chapter ingestion event for job {} and chapter {}", 
                event.getJobId(), event.getChapterId());

        try {
            // Fetch the job and chapter
            Optional<IngestionJob> jobOpt = jobRepository.findById(event.getJobId());
            Optional<Chapter> chapterOpt = chapterRepository.findById(event.getChapterId());

            if (jobOpt.isEmpty()) {
                log.error("Job not found for ID: {}", event.getJobId());
                return;
            }

            if (chapterOpt.isEmpty()) {
                log.error("Chapter not found for ID: {}", event.getChapterId());
                return;
            }

            IngestionJob job = jobOpt.get();
            Chapter chapter = chapterOpt.get();

            // Call the existing processing logic
            ingestionService.processChapter(job, chapter);

        } catch (Exception e) {
            log.error("Error processing chapter ingestion event for job {} and chapter {}", 
                    event.getJobId(), event.getChapterId(), e);
        }
    }
}
