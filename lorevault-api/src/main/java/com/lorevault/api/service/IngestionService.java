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
    private final ChunkService chunkService;

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
            
            // Process the existing chapter (v0.2.0: create chunks)
            processChapter(newJob, existingChapter.get());
            
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
        
        // Process the chapter (v0.2.0: create chunks)
        processChapter(job, chapter);

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
     * Process a chapter by creating chunks and tracking progress
     * v0.2.0: Implements content storage & segmentation
     */
    @Transactional
    protected void processChapter(IngestionJob job, Chapter chapter) {
        try {
            log.info("Starting chapter processing for job {} and chapter {}", job.getId(), chapter.getId());
            
            // Update job status to preprocessing
            updateJobStatus(job, IngestionStatus.PREPROCESSING_STARTED, 
                IngestionStatus.PREPROCESSING_STARTED.getProgressPercentage(), 
                "Starting content segmentation");
            
            // Check if chunks already exist
            if (chunkService.chunksExistForChapter(chapter.getId())) {
                log.info("Chunks already exist for chapter {}, skipping chunk creation", chapter.getId());
                updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, 
                    IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage(), 
                    "Chunks already exist, validating");
            } else {
                // Create chunks using deterministic segmentation
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, 
                    IngestionStatus.DETECTING_SCENES.getProgressPercentage(), 
                    "Performing deterministic text segmentation");
                
                List<Chunk> chunks = chunkService.createChunksForChapter(chapter);
                
                log.info("Created {} chunks for chapter {}", chunks.size(), chapter.getId());
                updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, 
                    IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage(),
                    String.format("Created %d chunks from chapter content", chunks.size()));
            }
            
            // Complete the job
            completeJob(job, chapter);
            
        } catch (Exception e) {
            log.error("Error processing chapter {} for job {}", chapter.getId(), job.getId(), e);
            failJob(job, "Chapter processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Update job status and create status record
     */
    @Transactional
    protected void updateJobStatus(IngestionJob job, IngestionStatus status, int progressPercent, String description) {
        job.setCurrentStatus(status);
        job.setProgressPercent(progressPercent);
        jobRepository.save(job);
        
        createStatusRecord(job.getId(), status, description, Map.of(
            "progressPercent", progressPercent,
            "timestamp", LocalDateTime.now().toString()
        ));
        
        log.debug("Updated job {} status to {} ({}%): {}", job.getId(), status, progressPercent, description);
    }
    
    /**
     * Mark job as completed successfully
     */
    @Transactional
    protected void completeJob(IngestionJob job, Chapter chapter) {
        int chunkCount = chunkService.getChunkCount(chapter.getId());
        
        job.setCurrentStatus(IngestionStatus.COMPLETE);
        job.setProgressPercent(100);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        createStatusRecord(
                job.getId(),
                IngestionStatus.COMPLETE,
                String.format("Chapter processing completed successfully. Created %d chunks.", chunkCount),
                Map.of(
                        "version", "0.2.0",
                        "pipeline", "content_segmentation",
                        "chunkCount", chunkCount,
                        "chapterLength", chapter.getRawText().length(),
                        "completedAt", LocalDateTime.now().toString()
                )
        );

        log.info("Job {} completed successfully with {} chunks", job.getId(), chunkCount);
    }
    
    /**
     * Mark job as failed
     */
    @Transactional
    protected void failJob(IngestionJob job, String errorMessage) {
        job.setCurrentStatus(IngestionStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        createStatusRecord(
                job.getId(),
                IngestionStatus.FAILED,
                errorMessage,
                Map.of(
                        "version", "0.2.0",
                        "failedAt", LocalDateTime.now().toString()
                )
        );

        log.error("Job {} failed: {}", job.getId(), errorMessage);
    }
}
