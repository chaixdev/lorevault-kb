package com.lorevault.api.service.ingestion;

import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.service.shared.HashService;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.ScenePersistenceService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.content.ChunkEmbeddingService;
import com.lorevault.api.graph.port.ContentPersistencePort;
import com.lorevault.api.graph.model.IngestionJobNode;
import com.lorevault.api.graph.model.ChapterNode;
import com.lorevault.api.graph.model.SceneNode;
import com.lorevault.api.graph.model.ChunkNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service for managing chapter ingestion and job lifecycle
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final HashService hashService;
    private final SceneDetectionService sceneDetectionService;
    private final ScenePersistenceService scenePersistenceService; // now graph-based
    private final TextChunkingService textChunkingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final ContentPersistencePort contentPersistencePort;

    /**
     * Submit a chapter for processing
     */
    @Transactional
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
        log.info("Processing chapter submission: {} - {}",
                request.getCoordinates(), request.getChapterTitle());

        String contentHash = hashService.generateSha256Hash(request.getChapterText());

        // Graph lookup for existing chapter
        try {
            var existingGraphChapter = contentPersistencePort.findChapterByContentHash(contentHash);
            if (existingGraphChapter.isPresent()) {
                UUID chapterId = existingGraphChapter.get().getId();
                if (contentPersistencePort.hasActiveJobForChapter(chapterId)) {
                    var active = contentPersistencePort.findMostRecentJobForChapter(chapterId);
                    if (active.isPresent()) {
                        return SubmitChapterResponse.success(active.get().getId(), chapterId);
                    }
                }
                IngestionJob job = createIngestionJob(chapterId);
                eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
                return SubmitChapterResponse.success(job.getId(), chapterId);
            }
        } catch (Exception e) {
            log.warn("Graph lookup failed: {}", e.getMessage());
        }

        // Create chapter in graph (source of truth)
        UUID chapterId;
        try {
            ChapterNode node = new ChapterNode();
            node.setId(UUID.randomUUID());
            if (request.getCoordinates() != null) {
                node.setUniverse(request.getCoordinates().getUniverse());
                node.setSeries(request.getCoordinates().getSeries());
                node.setBookNumber(request.getCoordinates().getBookNumber());
                node.setPartNumber(request.getCoordinates().getPartNumber());
                node.setChapterNumber(request.getCoordinates().getChapterNumber());
            }
            node.setChapterTitle(request.getChapterTitle());
            node.setRawText(request.getChapterText());
            node.setContentHash(contentHash);
            node = contentPersistencePort.createChapter(node);
            chapterId = node.getId();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create chapter in graph: " + e.getMessage(), e);
        }

        IngestionJob job = createIngestionJob(chapterId);
        eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
        return SubmitChapterResponse.success(job.getId(), chapterId);
    }

    /**
     * Get the status of an ingestion job
     */
    public Optional<JobStatusResponse> getJobStatus(UUID jobId) {
        // Prefer graph source first
        try {
            var jobNodeOpt = contentPersistencePort.findJob(jobId);
            if (jobNodeOpt.isPresent()) {
                var jobNode = jobNodeOpt.get();
                var recentNodes = contentPersistencePort.findRecentStatusRecords(jobId, 5);
                List<JobStatusResponse.StatusUpdateDto> recentUpdates = recentNodes.stream().map(n -> new JobStatusResponse.StatusUpdateDto(
                        n.getStatus(), n.getStepDescription(), n.getTimestamp(), n.getProgressPercent())).toList();
                JobStatusResponse response = new JobStatusResponse();
                response.setJobId(jobNode.getId());
                response.setChapterId(jobNode.getChapterId());
                response.setCurrentStatus(jobNode.getCurrentStatus());
                response.setProgressPercent(jobNode.getProgressPercent());
                response.setCreatedAt(jobNode.getCreatedAt());
                response.setCompletedAt(jobNode.getCompletedAt());
                if (jobNode.getCurrentStatus() != null) {
                    response.setIsComplete(jobNode.getCurrentStatus().isTerminal());
                }
                response.setRecentUpdates(recentUpdates);
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.warn("Graph job lookup failed: {}", e.getMessage());
        }
        return Optional.empty();
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
        
        try {
            IngestionJobNode node = new IngestionJobNode();
            node.setId(UUID.randomUUID());
            node.setChapterId(job.getChapterId());
            node.setCurrentStatus(job.getCurrentStatus());
            node.setProgressPercent(job.getProgressPercent());
            node.setCreatedAt(LocalDateTime.now());
            node = contentPersistencePort.createJob(node);
            job.setId(node.getId()); job.setCreatedAt(node.getCreatedAt());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create job in graph: " + e.getMessage(), e);
        }

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
        var node = new com.lorevault.api.graph.model.StatusRecordNode();
        node.setId(UUID.randomUUID());
        node.setJobId(jobId);
        node.setStatus(status);
        node.setStepDescription(description);
        node.setProgressPercent(status.getProgressPercentage());
        node.setTimestamp(LocalDateTime.now());
        try { contentPersistencePort.addStatusRecord(jobId, node); } catch (Exception e) { log.debug("Failed to add status record to graph: {}", e.getMessage()); }
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
            UUID chapterId = chapter.getId();
            String chapterText = chapter.getRawText();
            log.info("Starting v0.3.0 chapter processing for job {} and chapter {}", job.getId(), chapterId);
            updateJobStatus(job, IngestionStatus.PREPROCESSING_STARTED, IngestionStatus.PREPROCESSING_STARTED.getProgressPercentage(), "Starting AI-powered scene detection");

            List<SceneNode> sceneNodes;
            // Fetch existing scenes from graph
            List<SceneNode> existing = contentPersistencePort.findScenesByChapterId(chapterId);
            if (!existing.isEmpty()) {
                sceneNodes = existing;
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, IngestionStatus.DETECTING_SCENES.getProgressPercentage(), String.format("Found %d existing scenes, proceeding to chunking", sceneNodes.size()));
            } else {
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, IngestionStatus.DETECTING_SCENES.getProgressPercentage(), "Analyzing chapter text with AI to identify semantic scene boundaries");
                List<SceneWithCoordinates> scenesWithCoordinates = sceneDetectionService.detectScenesForChapter(chapterId);
                sceneNodes = scenePersistenceService.persistDetectedScenes(chapterId, scenesWithCoordinates);
                updateJobStatus(job, IngestionStatus.DETECTING_SCENES, IngestionStatus.DETECTING_SCENES.getProgressPercentage() + 10, String.format("Detected %d semantic scenes from chapter text", sceneNodes.size()));
            }

            updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage(), "Applying chunking decision gate to scenes");
            int chunkCount = createChunksFromScenes(chapterId, chapterText, sceneNodes);
            updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage() + 15, String.format("Created %d chunks from %d semantic scenes", chunkCount, sceneNodes.size()));

            updateJobStatus(job, IngestionStatus.EMBEDDING_CHUNKS, IngestionStatus.EMBEDDING_CHUNKS.getProgressPercentage() + 30, "Generating embeddings for chapter chunks");
            int embedded = chunkEmbeddingService.generateEmbeddingsForChapter(chapterId);
            log.info("Generated embeddings for {} chunks for chapter {}", embedded, chapterId);

            completeJob(job, chapterId, chapterText == null ? 0 : chapterText.length());
        } catch (Exception e) {
            log.error("Error processing chapter {} for job {}: {}", chapter.getId(), job.getId(), e.getMessage(), e);
            if (e.getMessage() != null && (e.getMessage().contains("LLM API") || e.getMessage().contains("scene detection failed") || e.getMessage().contains("Empty response") || e.getMessage().contains("failed permanently after multiple attempts"))) {
                log.warn("LLM API failure detected - cleaning up data for retry");
                failJobWithCleanup(job, "LLM API call failed: " + e.getMessage());
            } else {
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
        try {
            contentPersistencePort.findJob(job.getId()).ifPresent(node -> {
                node.setCurrentStatus(status);
                node.setProgressPercent(progressPercent);
                if (status.isTerminal()) {
                    node.setCompletedAt(LocalDateTime.now());
                }
                contentPersistencePort.updateJob(node);
            });
        } catch (Exception e) {
            log.debug("Graph job update failed for {}: {}", job.getId(), e.getMessage());
        }
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
    protected void completeJob(IngestionJob job, UUID chapterId, int chapterLength) {
        int chunkCount = contentPersistencePort.countChunksByChapterId(chapterId);
        
        job.setCurrentStatus(IngestionStatus.COMPLETE);
        job.setProgressPercent(100);
        job.setCompletedAt(LocalDateTime.now());
        try { contentPersistencePort.findJob(job.getId()).ifPresent(node -> { node.setCurrentStatus(IngestionStatus.COMPLETE); node.setProgressPercent(100); node.setCompletedAt(job.getCompletedAt()); contentPersistencePort.updateJob(node); }); } catch (Exception e) { log.debug("Graph completion update failed for job {}: {}", job.getId(), e.getMessage()); }
        createStatusRecord(job.getId(), IngestionStatus.COMPLETE, String.format("Chapter processing completed successfully. Created %d chunks.", chunkCount), Map.of("version", "0.2.0", "pipeline", "content_segmentation", "chunkCount", chunkCount, "chapterLength", chapterLength, "completedAt", LocalDateTime.now().toString()));
        log.info("Job {} completed successfully with {} chunks", job.getId(), chunkCount);
    }
    
    /**
     * Mark job as failed
     */
    @Transactional
    protected void failJob(IngestionJob job, String errorMessage) {
        job.setCurrentStatus(IngestionStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        try { contentPersistencePort.findJob(job.getId()).ifPresent(node -> { node.setCurrentStatus(IngestionStatus.FAILED); node.setCompletedAt(job.getCompletedAt()); contentPersistencePort.updateJob(node); }); } catch (Exception e) { log.debug("Graph fail update failed for job {}: {}", job.getId(), e.getMessage()); }
        createStatusRecord(job.getId(), IngestionStatus.FAILED, errorMessage, Map.of("version", "0.2.0", "failedAt", LocalDateTime.now().toString()));
        log.error("Job {} failed: {}", job.getId(), errorMessage);
    }
    
    /**
     * Mark job as failed and clean up any partially processed data
     * This allows a clean retry of the chapter later
     */
    @Transactional
    protected void failJobWithCleanup(IngestionJob job, String errorMessage) {
        UUID chapterId = job.getChapterId();
        int deletedChunks = contentPersistencePort.deleteChunksByChapterId(chapterId); log.info("Cleaned up {} chunks for failed chapter {} (graph)", deletedChunks, chapterId);
        int deletedScenes = contentPersistencePort.deleteScenesByChapterId(chapterId); log.info("Cleaned up {} scenes for failed chapter {} (graph)", deletedScenes, chapterId);
        failJob(job, errorMessage + " (data cleaned up for retry)");
    }

    /**
     * Creates chunks from scenes following the text-chunking specification.
     * Implements Stage 3 (Chunking Decision Gate) and Stage 4 (Chunk Generation):
     * Uses TextChunkingService which transparently handles both single and multi-chunk cases.
     */
    private int createChunksFromScenes(UUID chapterId, String chapterText, List<SceneNode> sceneNodes) {
        if (chapterText == null) return 0; List<ChunkNode> chunkNodes = new ArrayList<>();
        for (SceneNode scene : sceneNodes) {
            String sceneText = chapterText.substring(scene.getStartOffset().intValue(), scene.getEndOffset().intValue());
            List<Chunk> sceneChunks = textChunkingService.extractChunks(sceneText);
            for (Chunk chunk : sceneChunks) {
                chunk.setStartCharInChapter(chunk.getStartCharInChapter() + scene.getStartOffset().intValue());
                chunk.setEndCharInChapter(chunk.getEndCharInChapter() + scene.getStartOffset().intValue());
                String chunkContent = chapterText.substring(chunk.getStartCharInChapter(), chunk.getEndCharInChapter());
                String hash = hashService.generateSha256Hash(chunkContent);
                ChunkNode node = new ChunkNode(); node.setChunkNumberInChapter(chunk.getChunkNumberInChapter()); node.setStartCharInChapter(chunk.getStartCharInChapter()); node.setEndCharInChapter(chunk.getEndCharInChapter()); node.setContentHash(hash); chunkNodes.add(node);
            }
        }
        if (!chunkNodes.isEmpty()) { contentPersistencePort.addChunksToChapter(chapterId, chunkNodes); }
        log.info("Created {} total chunks from {} scenes (graph persisted)", chunkNodes.size(), sceneNodes.size());
        return chunkNodes.size();
    }
    /**
     * List jobs with optional universe and status filters and pagination.
     */
    public JobListResponse listJobs(String universe, String status, int limit, int offset) {
        List<IngestionJobNode> allJobs;
        try { if (universe != null && !universe.isBlank()) { var chapters = contentPersistencePort.findChaptersByUniverse(universe); var chapterIds = chapters.stream().map(ChapterNode::getId).toList(); allJobs = contentPersistencePort.findJobsByChapterIds(chapterIds); } else { allJobs = contentPersistencePort.findAllJobs(); } }
        catch (Exception e) { log.debug("Graph listJobs error: {}", e.getMessage()); return new JobListResponse(List.of(), new JobListResponse.Pagination(0, limit, offset, false)); }
        IngestionStatus statusEnumLocal = null; List<IngestionStatus> excludeStatusesLocal = null; if (status != null && !status.isBlank()) { if ("ACTIVE".equalsIgnoreCase(status)) { excludeStatusesLocal = List.of(IngestionStatus.COMPLETE, IngestionStatus.FAILED); } else { statusEnumLocal = IngestionStatus.valueOf(status.toUpperCase()); } }
        final IngestionStatus statusEnum = statusEnumLocal; final List<IngestionStatus> excludeStatuses = excludeStatusesLocal;
        Stream<IngestionJobNode> stream = allJobs.stream(); if (statusEnum != null) { stream = stream.filter(j -> statusEnum.equals(j.getCurrentStatus())); } else if (excludeStatuses != null) { stream = stream.filter(j -> j.getCurrentStatus() == null || !excludeStatuses.contains(j.getCurrentStatus())); }
        List<IngestionJobNode> filtered = stream.sorted((a,b) -> { if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0; if (a.getCreatedAt() == null) return 1; if (b.getCreatedAt() == null) return -1; return b.getCreatedAt().compareTo(a.getCreatedAt()); }).toList();
        long total = filtered.size(); int from = Math.min(offset, filtered.size()); int to = Math.min(from + limit, filtered.size()); List<IngestionJobNode> pageSlice = filtered.subList(from, to);
        List<JobListResponse.JobSummary> summaries = new ArrayList<>();
        for (IngestionJobNode jobNode : pageSlice) { JobListResponse.JobSummary s = new JobListResponse.JobSummary(); s.setJobId(jobNode.getId()); s.setChapterId(jobNode.getChapterId()); s.setStatus(jobNode.getCurrentStatus()); s.setProgress(jobNode.getProgressPercent()); s.setCreatedAt(jobNode.getCreatedAt()); s.setCompletedAt(jobNode.getCompletedAt()); contentPersistencePort.findChapterById(jobNode.getChapterId()).ifPresent(ch -> { s.setChapterTitle(ch.getChapterTitle()); s.setUniverse(ch.getUniverse()); s.setSeries(ch.getSeries()); s.setBookNumber(ch.getBookNumber()); s.setPartNumber(ch.getPartNumber()); s.setChapterNumber(ch.getChapterNumber()); }); summaries.add(s); }
        boolean hasMore = (long) (offset + limit) < total; JobListResponse.Pagination pagination = new JobListResponse.Pagination(total, limit, offset, hasMore); return new JobListResponse(summaries, pagination);
    }
}
