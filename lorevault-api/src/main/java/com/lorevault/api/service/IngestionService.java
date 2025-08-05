package com.lorevault.api.service;

import com.lorevault.api.dto.SubmitChapterRequest;
import com.lorevault.api.dto.SubmitChapterResponse;
import com.lorevault.api.dto.JobStatusResponse;
import com.lorevault.api.model.*;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.IngestionJobRepository;
import com.lorevault.api.repository.StatusRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing chapter ingestion and job lifecycle
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ChapterRepository chapterRepository;
    private final IngestionJobRepository jobRepository;
    private final StatusRecordRepository statusRecordRepository;
    private final HashService hashService;

    /**
     * Submit a chapter for processing
     */
    @Transactional
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
        log.info("Processing chapter submission: {} - {}", 
                request.getCoordinates(), request.getChapterTitle());

        // Generate content hash for deduplication
        String contentHash = hashService.generateSha256Hash(request.getChapterText());
        
        // Check if this content has already been processed
        Optional<Chapter> existingChapter = chapterRepository.findByContentHash(contentHash);
        if (existingChapter.isPresent()) {
            log.info("Content already exists with hash: {}", contentHash);
            
            // Check if there's already an active job for this chapter
            UUID existingChapterId = existingChapter.get().getId();
            if (jobRepository.hasActiveJobForChapter(existingChapterId)) {
                // Return the existing active job
                Optional<IngestionJob> activeJob = jobRepository.findMostRecentByChapterId(existingChapterId);
                if (activeJob.isPresent()) {
                    return SubmitChapterResponse.success(activeJob.get().getId(), existingChapterId);
                }
            }
            
            // Create a new job for the existing chapter
            IngestionJob newJob = createIngestionJob(existingChapterId);
            
            // For v0.1.0, immediately mark as complete (basic pipeline)
            completeJobImmediately(newJob);
            
            return SubmitChapterResponse.success(newJob.getId(), existingChapterId);
        }

        // Create new chapter
        Chapter chapter = new Chapter();
        chapter.setCoordinates(request.getCoordinates());
        chapter.setChapterTitle(request.getChapterTitle());
        chapter.setRawText(request.getChapterText());
        chapter.setContentHash(contentHash);
        
        chapter = chapterRepository.save(chapter);
        log.info("Created new chapter with ID: {}", chapter.getId());

        // Create ingestion job
        IngestionJob job = createIngestionJob(chapter.getId());
        
        // For v0.1.0, immediately mark as complete (basic pipeline)
        completeJobImmediately(job);

        return SubmitChapterResponse.success(job.getId(), chapter.getId());
    }

    /**
     * Get the status of an ingestion job
     */
    public Optional<JobStatusResponse> getJobStatus(UUID jobId) {
        Optional<IngestionJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        IngestionJob job = jobOpt.get();
        
        // Get recent status updates (last 5)
        List<StatusRecord> recentRecords = statusRecordRepository.findRecentByJobId(jobId)
                .stream()
                .limit(5)
                .collect(Collectors.toList());

        List<JobStatusResponse.StatusUpdateDto> recentUpdates = recentRecords.stream()
                .map(record -> new JobStatusResponse.StatusUpdateDto(
                        record.getStatus(),
                        record.getStepDescription(),
                        record.getTimestamp(),
                        record.getProgressPercent()
                ))
                .collect(Collectors.toList());

        JobStatusResponse response = new JobStatusResponse();
        response.setJobId(job.getId());
        response.setChapterId(job.getChapterId());
        response.setCurrentStatus(job.getCurrentStatus());
        response.setProgressPercent(job.getProgressPercent());
        response.setCreatedAt(job.getCreatedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setIsComplete(job.getCurrentStatus().isTerminal());
        response.setRecentUpdates(recentUpdates);

        return Optional.of(response);
    }

    /**
     * Create a new ingestion job and initial status record
     */
    @Transactional
    protected IngestionJob createIngestionJob(UUID chapterId) {
        // Create the job
        IngestionJob job = new IngestionJob();
        job.setChapterId(chapterId);
        job.setCurrentStatus(IngestionStatus.QUEUED);
        job.setProgressPercent(IngestionStatus.QUEUED.getProgressPercentage());
        
        job = jobRepository.save(job);
        log.info("Created ingestion job with ID: {}", job.getId());

        // Create initial status record
        createStatusRecord(
                job.getId(), 
                IngestionStatus.QUEUED, 
                "Chapter submitted and queued for processing",
                Map.of("chapterId", chapterId.toString())
        );

        return job;
    }

    /**
     * Create a status record for a job
     */
    @Transactional
    protected void createStatusRecord(UUID jobId, IngestionStatus status, String description, Map<String, Object> properties) {
        StatusRecord record = new StatusRecord();
        record.setJobId(jobId);
        record.setStatus(status);
        record.setStepDescription(description);
        record.setProgressPercent(status.getProgressPercentage());
        record.setProperties(properties);
        
        statusRecordRepository.save(record);
        log.debug("Created status record for job {}: {} - {}", jobId, status, description);
    }

    /**
     * For v0.1.0: Complete the job immediately with a simple pipeline
     * In future versions, this will be replaced with actual processing
     */
    @Transactional
    protected void completeJobImmediately(IngestionJob job) {
        // Update job to complete status
        job.setCurrentStatus(IngestionStatus.COMPLETE);
        job.setProgressPercent(100);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        // Create completion status record
        createStatusRecord(
                job.getId(),
                IngestionStatus.COMPLETE,
                "Chapter processing completed successfully (v0.1.0 basic pipeline)",
                Map.of(
                        "version", "0.1.0",
                        "pipeline", "basic",
                        "completedAt", LocalDateTime.now().toString()
                )
        );

        log.info("Job {} completed immediately (v0.1.0 basic pipeline)", job.getId());
    }
}
