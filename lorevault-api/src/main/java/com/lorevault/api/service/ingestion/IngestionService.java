package com.lorevault.api.service.ingestion;

import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.ChunkRepository;
import com.lorevault.api.repository.IngestionJobRepository;
import com.lorevault.api.repository.StatusRecordRepository;
import com.lorevault.api.service.shared.HashService;
import com.lorevault.api.service.content.ChunkService;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.ScenePersistenceService;
import com.lorevault.api.service.content.TextChunkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SceneDetectionService sceneDetectionService;
    private final ScenePersistenceService scenePersistenceService;
    private final TextChunkingService textChunkingService;
    private final ChunkRepository chunkRepository;
    private final ApplicationEventPublisher eventPublisher;

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
            
            // Publish event to process the existing chapter (v0.2.0: create chunks)
            eventPublisher.publishEvent(new ChapterIngestionEvent(this, newJob.getId(), existingChapterId));
            
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
        
        // Publish event to process the chapter (v0.2.0: create chunks)
        eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapter.getId()));

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
                .toList();

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
     * Process a chapter by detecting scenes and creating chunks following 
     * the v0.3.0 text-chunking specification.
     * Implements the four-stage workflow: Scene Identification → Coordinate Localization → 
     * Chunking Decision Gate → Chunk Generation
     */
    @Transactional
    public void processChapter(IngestionJob job, Chapter chapter) {
        try {
            log.info("Starting v0.3.0 chapter processing for job {} and chapter {}", job.getId(), chapter.getId());
            
            // Update job status to preprocessing
            updateJobStatus(job, IngestionStatus.PREPROCESSING_STARTED, 
                IngestionStatus.PREPROCESSING_STARTED.getProgressPercentage(), 
                "Starting AI-powered scene detection");
            
            // Check if scenes already exist for this chapter
            if (!chapter.getScenes().isEmpty()) {
                log.info("Scenes already exist for chapter {}, proceeding to chunking", chapter.getId());
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, 
                    IngestionStatus.DETECTING_SCENES.getProgressPercentage(), 
                    String.format("Found %d existing scenes, proceeding to chunking", chapter.getScenes().size()));
            } else {
                // Stage 1 & 2: AI Scene Detection + Coordinate Localization
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, 
                    IngestionStatus.DETECTING_SCENES.getProgressPercentage(), 
                    "Analyzing chapter text with AI to identify semantic scene boundaries");
                
                // Detect scenes (returns coordinates, no persistence)
                List<SceneWithCoordinates> scenesWithCoordinates = sceneDetectionService.detectScenesForChapter(chapter.getId());
                
                // Persist scenes in a separate transaction
                List<Scene> detectedScenes = scenePersistenceService.persistDetectedScenes(chapter.getId(), scenesWithCoordinates);
                
                log.info("Detected and persisted {} scenes for chapter {}", detectedScenes.size(), chapter.getId());
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, 
                    IngestionStatus.DETECTING_SCENES.getProgressPercentage() + 10,
                    String.format("Detected %d semantic scenes from chapter text", detectedScenes.size()));
                
                // Reload chapter to get updated scenes
                chapter = chapterRepository.findById(chapter.getId()).orElseThrow();
            }
            
            // Stage 3 & 4: Chunking Decision Gate + Chunk Generation
            updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, 
                IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage(), 
                "Applying chunking decision gate to scenes");
            
            List<Chunk> chunks = createChunksFromScenes(chapter);
            
            log.info("Created {} chunks from {} scenes for chapter {}", 
                     chunks.size(), chapter.getScenes().size(), chapter.getId());
            updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, 
                IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage() + 15,
                String.format("Created %d chunks from %d semantic scenes", chunks.size(), chapter.getScenes().size()));
            
            // Complete the job
            completeJob(job, chapter);
            
        } catch (Exception e) {
            log.error("Error processing chapter {} for job {}: {}", chapter.getId(), job.getId(), e.getMessage(), e);
            
            // Determine if this is an LLM API failure that should trigger cleanup for retry
            if (e.getMessage() != null && (
                e.getMessage().contains("LLM API") || 
                e.getMessage().contains("scene detection failed") ||
                e.getMessage().contains("Empty response") ||
                e.getMessage().contains("failed permanently after multiple attempts"))) {
                // For LLM API failures, clean up data to allow for fresh retry
                log.warn("LLM API failure detected - cleaning up data for retry");
                failJobWithCleanup(job, "LLM API call failed: " + e.getMessage());
            } else {
                // For other failures, just mark as failed
                failJob(job, "Chapter processing failed: " + e.getMessage());
            }
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
    
    /**
     * Mark job as failed and clean up any partially processed data
     * This allows a clean retry of the chapter later
     */
    @Transactional
    protected void failJobWithCleanup(IngestionJob job, String errorMessage) {
        // Get the chapter ID to clean up
        UUID chapterId = job.getChapterId();
        
        // Clean up any chunks created for this chapter
        int deletedChunks = chunkRepository.deleteAllByChapterId(chapterId);
        log.info("Cleaned up {} chunks for failed chapter {}", deletedChunks, chapterId);
        
        // Clean up any scenes created for this chapter
        int deletedScenes = scenePersistenceService.deleteAllScenesForChapter(chapterId);
        log.info("Cleaned up {} scenes for failed chapter {}", deletedScenes, chapterId);
        
        // Mark the job as failed
        failJob(job, errorMessage + " (data cleaned up for retry)");
    }

    /**
     * Creates chunks from scenes following the text-chunking specification.
     * Implements Stage 3 (Chunking Decision Gate) and Stage 4 (Chunk Generation):
     * Uses TextChunkingService which transparently handles both single and multi-chunk cases.
     */
    private List<Chunk> createChunksFromScenes(Chapter chapter) {
        List<Chunk> allChunks = new ArrayList<>();
        String chapterText = chapter.getRawText();
        
        log.debug("Processing {} scenes for chunking", chapter.getScenes().size());
        
        for (Scene scene : chapter.getScenes()) {
            long sceneLength = scene.getEndCharacterOffset() - scene.getStartCharacterOffset();
            
            log.debug("Processing scene {} with {} chars", scene.getSceneIndex(), sceneLength);
            
            String sceneText = chapterText.substring(
                scene.getStartCharacterOffset().intValue(), 
                scene.getEndCharacterOffset().intValue()
            );
            
            // Use TextChunkingService which handles threshold logic transparently
            // Returns 1 chunk if ≤ threshold, multiple chunks if > threshold
            List<Chunk> sceneChunks = textChunkingService.extractChunks(sceneText);
            
            // Adjust chunk coordinates to be relative to chapter and set scene relationship
            for (Chunk chunk : sceneChunks) {
                // Adjust coordinates to be relative to chapter, not scene
                chunk.setStartCharInChapter(chunk.getStartCharInChapter() + scene.getStartCharacterOffset().intValue());
                chunk.setEndCharInChapter(chunk.getEndCharInChapter() + scene.getStartCharacterOffset().intValue());

                // Set relationships
                chunk.setChapter(chapter);
                chunk.setScene(scene);

                // Generate content hash
                String chunkContent = chapterText.substring(
                        chunk.getStartCharInChapter(),
                        chunk.getEndCharInChapter()
                );
                chunk.setContentHash(hashService.generateSha256Hash(chunkContent));
            }
            
            // Save the scene chunks
            allChunks.addAll(chunkRepository.saveAll(sceneChunks));
        }
        
        log.info("Created {} total chunks from {} scenes", allChunks.size(), chapter.getScenes().size());
        return allChunks;
    }

    /**
     * List jobs with optional universe and status filters and pagination.
     */
    public JobListResponse listJobs(String universe, String status, int limit, int offset) {
        int page = offset / Math.max(1, limit);
        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<IngestionJob> pageResult;

        // Determine status filter
        IngestionStatus statusEnum = null;
        List<IngestionStatus> excludeStatuses = null;
        if (status != null && !status.isBlank()) {
            if ("ACTIVE".equalsIgnoreCase(status)) {
                excludeStatuses = List.of(IngestionStatus.COMPLETE, IngestionStatus.FAILED);
            } else {
                statusEnum = IngestionStatus.valueOf(status.toUpperCase());
            }
        }

        // Universe filter handled via chapter IDs
        List<java.util.UUID> chapterIds = null;
        if (universe != null && !universe.isBlank()) {
            chapterIds = chapterRepository.findChapterIdsByUniverse(universe);
            if (chapterIds.isEmpty()) {
                return new JobListResponse(List.of(), new JobListResponse.Pagination(0, limit, offset, false));
            }
        }

        if (chapterIds == null && statusEnum == null && excludeStatuses == null) {
            pageResult = jobRepository.findAll(pageable);
        } else if (chapterIds == null) {
            if (statusEnum != null) {
                pageResult = jobRepository.findByCurrentStatus(statusEnum, pageable);
            } else {
                pageResult = jobRepository.findByCurrentStatusNotIn(excludeStatuses, pageable);
            }
        } else {
            if (statusEnum != null) {
                pageResult = jobRepository.findByChapterIdInAndCurrentStatus(chapterIds, statusEnum, pageable);
            } else if (excludeStatuses != null) {
                pageResult = jobRepository.findByChapterIdInAndCurrentStatusNotIn(chapterIds, excludeStatuses, pageable);
            } else {
                pageResult = jobRepository.findByChapterIdIn(chapterIds, pageable);
            }
        }

        List<JobListResponse.JobSummary> summaries = new java.util.ArrayList<>();
        for (IngestionJob job : pageResult.getContent()) {
            JobListResponse.JobSummary s = new JobListResponse.JobSummary();
            s.setJobId(job.getId());
            s.setChapterId(job.getChapterId());
            s.setStatus(job.getCurrentStatus());
            s.setProgress(job.getProgressPercent());
            s.setCreatedAt(job.getCreatedAt());
            s.setCompletedAt(job.getCompletedAt());

            // Fetch chapter metadata
            chapterRepository.findChapterTitleById(job.getChapterId()).ifPresent(s::setChapterTitle);
            var coordsList = chapterRepository.findCoordinatesById(job.getChapterId());
            if (!coordsList.isEmpty()) {
                Object[] c = coordsList.get(0);
                s.setUniverse((String) c[0]);
                s.setSeries((String) c[1]);
                s.setBookNumber((Integer) c[2]);
                s.setPartNumber((Integer) c[3]);
                s.setChapterNumber((Integer) c[4]);
            }

            summaries.add(s);
        }

        long total = pageResult.getTotalElements();
        boolean hasMore = (long) (offset + limit) < total;
        JobListResponse.Pagination pagination = new JobListResponse.Pagination(total, limit, offset, hasMore);
        return new JobListResponse(summaries, pagination);
    }
}
