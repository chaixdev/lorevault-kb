package com.lorevault.api.service.ingestion;

import com.lorevault.api.domain.ingestion.StatusRecord;
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
import com.lorevault.api.infrastructure.persistence.neo4j.mapping.GraphModelMapper;
import com.lorevault.api.service.shared.HashService;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.ScenePersistenceService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.content.ChunkEmbeddingService;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.StatusRecordNode;
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
    private final GraphModelMapper mapper;

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
                var recentNodes = contentPersistencePort.findStatusHistoryForJob(jobId);
                List<JobStatusResponse.StatusUpdateDto> recentUpdates = recentNodes.stream().map(n -> new JobStatusResponse.StatusUpdateDto(
                        n.getStatus(), n.getStepDescription(), n.getTimestamp(), n.getProgressPercent())).toList();
                JobStatusResponse response = new JobStatusResponse();
                response.setJobId(jobNode.getId());
                response.setChapterId(jobNode.getChapterId());
                var cur = jobNode.getCurrentStatusRecord();
                if (cur != null) {
                    response.setCurrentStatus(cur.getStatus());
                    response.setProgressPercent(cur.getProgressPercent());
                    response.setIsComplete(cur.getStatus().isTerminal());
                }
                response.setCreatedAt(jobNode.getCreatedAt());
                response.setCompletedAt(jobNode.getCompletedAt());
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
        // Persist the job first to obtain the definitive jobId
        IngestionJobNode node;
        try {
            node = new IngestionJobNode();
            node.setId(UUID.randomUUID());
            node.setChapterId(chapterId);
            node.setCreatedAt(LocalDateTime.now());
            node = contentPersistencePort.createJob(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create job in graph: " + e.getMessage(), e);
        }

        // Build the domain aggregate from persisted data
        IngestionJob job = new IngestionJob();
        job.setId(node.getId());
        job.setChapterId(chapterId);
        job.setCreatedAt(node.getCreatedAt());

        // Create and persist the initial status record using the persisted jobId
        StatusRecord sr = new StatusRecord(
                UUID.randomUUID(),
                node.getId(),
                LocalDateTime.now(),
                IngestionStatus.QUEUED,
                "Chapter submitted and queued for processing",
                Map.of("chapterId", chapterId.toString())
        );
        job.setCurrentStatus(sr);
        updateJobNodeStatus(sr);

        return job;
    }

    /**
     * Create a status record for a job
     */
    @Transactional
    protected void updateJobNodeStatus(StatusRecord sr) {
        var node = new StatusRecordNode();
        node.setId(UUID.randomUUID());
        node.setJobId(sr.getJobId());
        node.setStatus(sr.getStatus());
        node.setStepDescription(sr.getStepDescription());
        node.setProgressPercent(sr.getStatus().getProgressPercentage());
        node.setTimestamp(sr.getTimestamp());
        try {
            contentPersistencePort.addStatusRecord(sr.getJobId(), node);
        } catch (Exception e) {
            log.debug("Failed to add status record to graph: {}", e.getMessage());
        }
        log.debug("Created status record for job {}: {} - {}", sr.getJobId(), sr.getStatus(), sr.getStepDescription());
    }

    /**
     * Create a status record for a job
     */
    @Transactional
    protected void updateJobNodeStatus(UUID jobId, IngestionStatus status, String description, Map<String, Object> properties) {
        var node = new StatusRecordNode();
        node.setId(UUID.randomUUID());
        node.setJobId(jobId);
        node.setStatus(status);
        node.setStepDescription(description);
        node.setProgressPercent(status.getProgressPercentage());
        node.setTimestamp(LocalDateTime.now());
        try {
            contentPersistencePort.addStatusRecord(jobId, node);
        } catch (Exception e) {
            log.debug("Failed to add status record to graph: {}", e.getMessage());
        }
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
            updateJobNodeStatus(job.getId(), IngestionStatus.PREPROCESSING_STARTED, "Starting AI-powered scene detection", Collections.emptyMap());

            List<SceneNode> sceneNodes;
            // Fetch existing scenes from graph
            List<SceneNode> existing = contentPersistencePort.findScenesByChapterId(chapterId);
            if (!existing.isEmpty()) {
                sceneNodes = existing;
                updateJobNodeStatus(job.getId(), IngestionStatus.DETECTING_SCENES, String.format("Found %d existing scenes, proceeding to chunking", sceneNodes.size()), Collections.emptyMap());
            } else {
                updateJobNodeStatus(job.getId(), IngestionStatus.DETECTING_SCENES, "Analyzing chapter text with AI to identify semantic scene boundaries", Collections.emptyMap());
                List<SceneWithCoordinates> scenesWithCoordinates = sceneDetectionService.detectScenesForChapter(chapterId);
                sceneNodes = scenePersistenceService.persistDetectedScenes(chapterId, scenesWithCoordinates);
                updateJobNodeStatus(job.getId(), IngestionStatus.DETECTING_SCENES, String.format("Detected %d semantic scenes from chapter text", sceneNodes.size()), Collections.emptyMap());
            }

            updateJobNodeStatus(job.getId(), IngestionStatus.EMBEDDING_CHUNKS, "Applying chunking decision gate to scenes", Collections.emptyMap());
            int chunkCount = createChunksFromScenes(chapterId, chapterText, sceneNodes);
            updateJobNodeStatus(job.getId(), IngestionStatus.EMBEDDING_CHUNKS, String.format("Created %d chunks from %d semantic scenes", chunkCount, sceneNodes.size()), Collections.emptyMap());

            updateJobNodeStatus(job.getId(), IngestionStatus.EMBEDDING_CHUNKS, "Generating embeddings for chapter chunks", Collections.emptyMap());
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
     * Mark job as completed successfully
     */
    @Transactional
    protected void completeJob(IngestionJob job, UUID chapterId, int chapterLength) {
        int chunkCount = contentPersistencePort.countChunksByChapterId(chapterId);

        StatusRecord sr = new StatusRecord(
                UUID.randomUUID(),
                job.getId(),
                LocalDateTime.now(),
                IngestionStatus.COMPLETE,
                String.format("Chapter processing completed successfully. Created %d chunks.", chunkCount),
                Map.of("version", "0.2.0",
                        "pipeline", "content_segmentation",
                        "chunkCount", chunkCount,
                        "chapterLength", chapterLength,
                        "completedAt", LocalDateTime.now().toString()));

        job.setCurrentStatus(sr);
        job.setCompletedAt(LocalDateTime.now());
        updateJobNodeStatus(sr);
        // Also persist completedAt on the Job node
        try {
            contentPersistencePort.findJob(job.getId()).ifPresent(n -> {
                n.setCompletedAt(job.getCompletedAt());
                contentPersistencePort.updateJob(n);
            });
        } catch (Exception e) {
            log.debug("Graph completion update failed for job {}: {}", job.getId(), e.getMessage());
        }
        log.info("Job {} completed successfully with {} chunks", job.getId(), chunkCount);
    }

    /**
     * Mark job as failed
     */
    @Transactional
    protected void failJob(IngestionJob job, String errorMessage) {
        StatusRecord sr = new StatusRecord(UUID.randomUUID(), job.getId(), LocalDateTime.now(), IngestionStatus.FAILED, errorMessage,
                Map.of("version", "0.2.0", "failedAt", LocalDateTime.now().toString()));
        job.setCurrentStatus(sr);
        job.setCompletedAt(LocalDateTime.now());
        updateJobNodeStatus(sr);
        // Also persist completedAt on the Job node
        try {
            contentPersistencePort.findJob(job.getId()).ifPresent(n -> {
                n.setCompletedAt(job.getCompletedAt());
                contentPersistencePort.updateJob(n);
            });
        } catch (Exception e) {
            log.debug("Graph fail update failed for job {}: {}", job.getId(), e.getMessage());
        }
        log.error("Job {} failed: {}", job.getId(), errorMessage);
    }

    /**
     * Mark job as failed and clean up any partially processed data
     * This allows a clean retry of the chapter later
     */
    @Transactional
    protected void failJobWithCleanup(IngestionJob job, String errorMessage) {
        UUID chapterId = job.getChapterId();
        int deletedChunks = contentPersistencePort.deleteChunksByChapterId(chapterId);
        log.info("Cleaned up {} chunks for failed chapter {} (graph)", deletedChunks, chapterId);
        int deletedScenes = contentPersistencePort.deleteScenesByChapterId(chapterId);
        log.info("Cleaned up {} scenes for failed chapter {} (graph)", deletedScenes, chapterId);
        failJob(job, errorMessage + " (data cleaned up for retry)");
    }

    /**
     * Creates chunks from scenes following the text-chunking specification.
     * Implements Stage 3 (Chunking Decision Gate) and Stage 4 (Chunk Generation):
     * Uses TextChunkingService which transparently handles both single and multi-chunk cases.
     */
    private int createChunksFromScenes(UUID chapterId, String chapterText, List<SceneNode> sceneNodes) {
        if (chapterText == null) return 0; 
        List<ChunkNode> chunkNodes = new ArrayList<>();
        for (SceneNode scene : sceneNodes) {
            String sceneText = chapterText.substring(scene.getStartOffset().intValue(), scene.getEndOffset().intValue());
            List<Chunk> sceneChunks = textChunkingService.extractChunks(sceneText);
            for (Chunk chunk : sceneChunks) {
                chunk.setStartCharInChapter(chunk.getStartCharInChapter() + scene.getStartOffset().intValue());
                chunk.setEndCharInChapter(chunk.getEndCharInChapter() + scene.getStartOffset().intValue());
                String chunkContent = chapterText.substring(chunk.getStartCharInChapter(), chunk.getEndCharInChapter());
                String hash = hashService.generateSha256Hash(chunkContent);
                ChunkNode node = new ChunkNode();
                node.setChunkNumberInChapter(chunk.getChunkNumberInChapter());
                node.setStartCharInChapter(chunk.getStartCharInChapter());
                node.setEndCharInChapter(chunk.getEndCharInChapter());
                node.setContentHash(hash);
                chunkNodes.add(node);
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
        try {
            if (universe != null && !universe.isBlank()) {
                var chapters = contentPersistencePort.findChaptersByUniverse(universe);
                var chapterIds = chapters.stream().map(ChapterNode::getId).toList();
                allJobs = contentPersistencePort.findJobsByChapterIds(chapterIds);
            } else {
                allJobs = contentPersistencePort.findAllJobs();
            }
        } catch (Exception e) {
            log.debug("Graph listJobs error: {}", e.getMessage());
            return new JobListResponse(List.of(), new JobListResponse.Pagination(0, limit, offset, false));
        }
        IngestionStatus statusEnumLocal = null;
        List<IngestionStatus> excludeStatusesLocal = null;
        if (status != null && !status.isBlank()) {
            if ("ACTIVE".equalsIgnoreCase(status)) {
                excludeStatusesLocal = List.of(IngestionStatus.COMPLETE, IngestionStatus.FAILED);
            } else {
                statusEnumLocal = IngestionStatus.valueOf(status.toUpperCase());
            }
        }
        final IngestionStatus statusEnum = statusEnumLocal;
        final List<IngestionStatus> excludeStatuses = excludeStatusesLocal;
        Stream<IngestionJobNode> stream = allJobs.stream();
        if (statusEnum != null) {
            stream = stream.filter(j -> j.getCurrentStatusRecord() != null && statusEnum.equals(j.getCurrentStatusRecord().getStatus()));
        } else if (excludeStatuses != null) {
            stream = stream.filter(j -> j.getCurrentStatusRecord() == null || !excludeStatuses.contains(j.getCurrentStatusRecord().getStatus()));
        }
        List<IngestionJobNode> filtered = stream.sorted((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        }).toList();
        long total = filtered.size();
        int from = Math.min(offset, filtered.size());
        int to = Math.min(from + limit, filtered.size());
        List<IngestionJobNode> pageSlice = filtered.subList(from, to);
        List<JobListResponse.JobSummary> summaries = new ArrayList<>();
        for (IngestionJobNode jobNode : pageSlice) {
            JobListResponse.JobSummary s = new JobListResponse.JobSummary();
            s.setJobId(jobNode.getId());
            s.setChapterId(jobNode.getChapterId());
            var cur = jobNode.getCurrentStatusRecord();
            if (cur != null) {
                s.setStatus(cur.getStatus());
                s.setProgress(cur.getProgressPercent());
            }
            s.setCreatedAt(jobNode.getCreatedAt());
            s.setCompletedAt(jobNode.getCompletedAt());
            contentPersistencePort.findChapterById(jobNode.getChapterId()).ifPresent(ch -> {
                s.setChapterTitle(ch.getChapterTitle());
                s.setUniverse(ch.getUniverse());
                s.setSeries(ch.getSeries());
                s.setBookNumber(ch.getBookNumber());
                s.setPartNumber(ch.getPartNumber());
                s.setChapterNumber(ch.getChapterNumber());
            });
            summaries.add(s);
        }
        boolean hasMore = (long) (offset + limit) < total;
        JobListResponse.Pagination pagination = new JobListResponse.Pagination(total, limit, offset, hasMore);
        return new JobListResponse(summaries, pagination);
    }
}
