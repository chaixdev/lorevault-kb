package com.lorevault.api.service.ingestion;

import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing chapter ingestion orchestration.
 * Refactored to delegate responsibilities to focused services:
 * - ChapterValidationService: handles validation and duplicate detection
 * - IngestionJobService: manages job lifecycle and query operations (consolidated)
 * - IngestionWorkflowService: orchestrates the processing pipeline (via ChapterProcessor)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ChapterValidationService chapterValidationService;
    private final IngestionJobService ingestionJobService;
    private final IngestionWorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Submit a chapter for processing using extracted validation and job lifecycle services
     */
    @Transactional
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
    log.info("Processing chapter submission: bookId={}, chapterNumber={}, title={}",
        request.getBookId(), request.getChapterNumber(), request.getChapterTitle());

        // Validate chapter and handle duplicates
    ChapterValidationService.ChapterValidationResult validationResult = 
                chapterValidationService.validateAndProcessChapter(request);

        UUID chapterId = validationResult.getChapterId();

        // Handle existing chapter case
        if (validationResult.isExistingChapter()) {
            if (validationResult.hasActiveJob()) {
                Optional<UUID> activeJobId = chapterValidationService.findMostRecentJobId(chapterId);
                if (activeJobId.isPresent()) {
                    return SubmitChapterResponse.success(activeJobId.get(), chapterId);
                }
            }
            
            // Create new job for existing chapter
            IngestionJob job = ingestionJobService.createIngestionJob(chapterId);
            eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
            return SubmitChapterResponse.success(job.getId(), chapterId);
        }

        // Create job for new chapter
        IngestionJob job = ingestionJobService.createIngestionJob(chapterId);
        eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
        return SubmitChapterResponse.success(job.getId(), chapterId);
    }

    /**
     * Get the status of an ingestion job using consolidated IngestionJobService
     */
    public Optional<JobStatusResponse> getJobStatus(UUID jobId) {
        return ingestionJobService.getJobStatus(jobId);
    }

    /**
     * Process a chapter using the workflow service
     */
    public void processChapter(IngestionJob job, Chapter chapter) {
        workflowService.processChapter(job, chapter);
    }

    /**
     * List jobs using consolidated IngestionJobService with pagination and filtering
     */
    public JobListResponse listJobs(String universe, String status, int limit, int offset) {
        return ingestionJobService.listJobs(universe, status, limit, offset);
    }
}
