package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.StatusRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for managing the lifecycle of ingestion jobs.
 * Handles job creation, status updates, completion, and failure scenarios.
 * Extracted from IngestionService to improve single responsibility and testability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionJobLifecycleService {

    private final ContentPersistencePort contentPersistencePort;

    /**
     * Create a new ingestion job and initial status record
     */
    @Transactional
    public IngestionJob createIngestionJob(UUID chapterId) {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(chapterId);
        job.setCreatedAt(LocalDateTime.now());

        IngestionJob persistedJob = contentPersistencePort.createJobWithChapter(job, chapterId);

        StatusRecord initialStatus = createInitialStatusRecord(persistedJob.getId(), chapterId);
        contentPersistencePort.addStatusRecord(persistedJob.getId(), initialStatus);
        
        persistedJob.setCurrentStatus(initialStatus);
        return persistedJob;
    }

    /**
     * Mark job as completed successfully
     */
    @Transactional
    public void completeJob(IngestionJob job, UUID chapterId, int chapterLength) {
        int chunkCount = contentPersistencePort.countChunksByChapterId(chapterId);
        
        StatusRecord completionStatus = new StatusRecord(
                UUID.randomUUID(),
                job.getId(),
                LocalDateTime.now(),
                IngestionStatus.COMPLETE,
                String.format("Chapter processing completed successfully. Created %d chunks.", chunkCount),
                100, // progressPercent
                Map.of(
                    "version", "0.2.0",
                    "pipeline", "content_segmentation",
                    "chunkCount", chunkCount,
                    "chapterLength", chapterLength,
                    "completedAt", LocalDateTime.now().toString()
                )
        );

        job.setCurrentStatus(completionStatus);
        job.setCompletedAt(LocalDateTime.now());
        saveStatusRecord(completionStatus);
        updateJobCompletedAt(job.getId(), job.getCompletedAt());
        
        log.info("Job {} completed successfully with {} chunks", job.getId(), chunkCount);
    }

    /**
     * Mark job as failed
     */
    @Transactional
    public void failJob(IngestionJob job, String errorMessage) {
        StatusRecord failureStatus = new StatusRecord(
                UUID.randomUUID(), 
                job.getId(), 
                LocalDateTime.now(), 
                IngestionStatus.FAILED, 
                errorMessage,
                0, // progressPercent - failed jobs have 0 progress
                Map.of(
                    "version", "0.2.0", 
                    "failedAt", LocalDateTime.now().toString()
                )
        );
        
        job.setCurrentStatus(failureStatus);
        job.setCompletedAt(LocalDateTime.now());
        saveStatusRecord(failureStatus);
        updateJobCompletedAt(job.getId(), job.getCompletedAt());
        
        log.error("Job {} failed: {}", job.getId(), errorMessage);
    }

    /**
     * Mark job as failed and clean up any partially processed data
     * This allows a clean retry of the chapter later
     */
    @Transactional
    public void failJobWithCleanup(IngestionJob job, String errorMessage) {
        UUID chapterId = job.getChapterId();
        
        // Clean up partially processed data
        int deletedChunks = contentPersistencePort.deleteChunksByChapterId(chapterId);
        log.info("Cleaned up {} chunks for failed chapter {} (graph)", deletedChunks, chapterId);
        
        int deletedScenes = contentPersistencePort.deleteScenesByChapterId(chapterId);
        log.info("Cleaned up {} scenes for failed chapter {} (graph)", deletedScenes, chapterId);
        
        failJob(job, errorMessage + " (data cleaned up for retry)");
    }

    /**
     * Update job status with new status record
     */
    @Transactional
    public void updateJobStatus(UUID jobId, IngestionStatus status, String description, Map<String, Object> properties) {
        StatusRecord statusRecord = new StatusRecord(
            UUID.randomUUID(),
            jobId,
            LocalDateTime.now(),
            status,
            description,
            status.getProgressPercentage(),
            properties
        );
        
        try {
            contentPersistencePort.addStatusRecord(jobId, statusRecord);
            log.debug("Created status record for job {}: {} - {}", jobId, status, description);
        } catch (Exception e) {
            log.debug("Failed to add status record to graph: {}", e.getMessage());
        }
    }

    private StatusRecord createInitialStatusRecord(UUID jobId, UUID chapterId) {
        return new StatusRecord(
                UUID.randomUUID(),
                jobId,
                LocalDateTime.now(),
                IngestionStatus.QUEUED,
                "Chapter submitted and queued for processing",
                0, // progressPercent
                Map.of("chapterId", chapterId.toString())
        );
    }

    private void saveStatusRecord(StatusRecord statusRecord) {
        try {
            contentPersistencePort.addStatusRecord(statusRecord.getJobId(), statusRecord);
            log.debug("Created status record for job {}: {} - {}", 
                    statusRecord.getJobId(), statusRecord.getStatus(), statusRecord.getStepDescription());
        } catch (Exception e) {
            log.debug("Failed to add status record to graph: {}", e.getMessage());
        }
    }

    private void updateJobCompletedAt(UUID jobId, LocalDateTime completedAt) {
        try {
            contentPersistencePort.findJob(jobId).ifPresent(job -> {
                job.setCompletedAt(completedAt);
                contentPersistencePort.updateJob(job);
            });
        } catch (Exception e) {
            log.debug("Graph completion update failed for job {}: {}", jobId, e.getMessage());
        }
    }
}
