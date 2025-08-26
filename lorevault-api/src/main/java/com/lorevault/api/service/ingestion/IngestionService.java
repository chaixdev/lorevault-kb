package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.JobContextPort;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.service.content.ChunkEmbeddingService;
import com.lorevault.api.service.content.SceneProcessingService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.timeline.DefaultTemporalEdgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.lorevault.api.util.HashUtils.generateSha256Hash;

/**
 * Service for managing chapter ingestion orchestration.
 * Handles both ingestion workflow coordination and chapter validation internally.
 * Consolidated from separate validation service to eliminate unnecessary indirection.
 * 
 * Responsibilities:
 * - Chapter validation and duplicate detection
 * - Ingestion job management (via IngestionJobService) 
 * - Processing pipeline orchestration (via IngestionWorkflowService)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ContentPersistencePort contentPersistencePort;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;
    
    // Direct workflow dependencies (no more workflow service)
    private final JobContextPort jobContextPort;
    private final SceneProcessingService sceneProcessingService;
    private final TextChunkingService textChunkingService;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final DefaultTemporalEdgeService defaultTemporalEdgeService;

    /**
     * Context object for chapter validation results
     */
    public static class ChapterValidationResult {
        private final boolean isExistingChapter;
        private final UUID chapterId;
        private final String contentHash;
        private final boolean hasActiveJob;

        private ChapterValidationResult(boolean isExistingChapter, UUID chapterId, String contentHash, boolean hasActiveJob) {
            this.isExistingChapter = isExistingChapter;
            this.chapterId = chapterId;
            this.contentHash = contentHash;
            this.hasActiveJob = hasActiveJob;
        }

        public static ChapterValidationResult existingChapter(UUID chapterId, String contentHash, boolean hasActiveJob) {
            return new ChapterValidationResult(true, chapterId, contentHash, hasActiveJob);
        }

        public static ChapterValidationResult newChapter(UUID chapterId, String contentHash) {
            return new ChapterValidationResult(false, chapterId, contentHash, false);
        }

        public boolean isExistingChapter() { return isExistingChapter; }
        public UUID getChapterId() { return chapterId; }
        public String getContentHash() { return contentHash; }
        public boolean hasActiveJob() { return hasActiveJob; }
    }

    /**
     * Submit a chapter for processing with integrated validation and duplicate detection
     */
    @Transactional
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
        log.info("Processing chapter submission: bookId={}, chapterNumber={}, title={}",
            request.getBookId(), request.getChapterNumber(), request.getChapterTitle());

        // Validate chapter and handle duplicates
        ChapterValidationResult validationResult = validateAndProcessChapter(request);

        UUID chapterId = validationResult.getChapterId();

        // Handle existing chapter case
        if (validationResult.isExistingChapter()) {
            if (validationResult.hasActiveJob()) {
                Optional<UUID> activeJobId = findMostRecentJobId(chapterId);
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
     * Process a chapter using direct workflow orchestration
     * Handles the four-stage processing pipeline: Scene Detection → Coordinate Localization → 
     * Chunking Decision Gate → Chunk Generation and Embedding.
     */
    @Transactional
    public void processChapter(IngestionJob job, Chapter chapter) {
        WorkflowContext context = new WorkflowContext(job, chapter, chapter.getRawText());
        
        try {
            log.info("Starting v0.3.0 chapter processing for job {} and chapter {}", 
                    context.getJobId(), context.getChapterId());
            
            // Set job ID for retry-aware scene detection
            jobContextPort.setCurrentJobId(context.getJobId());
            
            updateStatus(context, IngestionStatus.PREPROCESSING_STARTED, 
                    "Starting AI-powered scene detection");

            List<Scene> scenes = executeSceneDetectionStage(context);
            executeChunkingStage(context, scenes);
            executeEmbeddingStage(context);

            ingestionJobService.completeJob(job, context.getChapterId(), 
                    getChapterLength(context.getChapterText()));
            
        } catch (Exception e) {
            handleProcessingError(context, e);
        } finally {
            // Always clear job ID from ThreadLocal to prevent memory leaks
            jobContextPort.clearCurrentJobId();
        }
    }

    /**
     * List jobs using consolidated IngestionJobService with pagination and filtering
     */
    public JobListResponse listJobs(String universe, String status, int limit, int offset) {
        return ingestionJobService.listJobs(universe, status, limit, offset);
    }

    // ========== Private Chapter Validation Methods ==========
    // Consolidated from ChapterValidationService to eliminate unnecessary indirection

    /**
     * Validate chapter submission and handle duplicate detection
     */
    @Transactional
    private ChapterValidationResult validateAndProcessChapter(SubmitChapterRequest request) {
        log.info("Validating chapter submission: bookId={}, chapterNumber={}, title={}",
            request.getBookId(), request.getChapterNumber(), request.getChapterTitle());

        String contentHash = generateSha256Hash(request.getChapterText());

        // Check for existing chapter with same content
        Optional<Chapter> existingChapter = findExistingChapterByHash(contentHash);
        if (existingChapter.isPresent()) {
            UUID chapterId = existingChapter.get().getId();
            boolean hasActiveJob = checkForActiveJob(chapterId);
            return ChapterValidationResult.existingChapter(chapterId, contentHash, hasActiveJob);
        }

        // Create new chapter
        UUID newChapterId = createNewChapter(request, contentHash);
        return ChapterValidationResult.newChapter(newChapterId, contentHash);
    }

    /**
     * Check if a chapter has an active processing job
     */
    private boolean checkForActiveJob(UUID chapterId) {
        try {
            return contentPersistencePort.hasActiveJobForChapter(chapterId);
        } catch (Exception e) {
            log.warn("Failed to check for active job for chapter {}: {}", chapterId, e.getMessage());
            return false;
        }
    }

    /**
     * Find the most recent job for a chapter
     */
    private Optional<UUID> findMostRecentJobId(UUID chapterId) {
        try {
            return contentPersistencePort.findMostRecentJobForChapter(chapterId)
                    .map(job -> job.getId());
        } catch (Exception e) {
            log.warn("Failed to find most recent job for chapter {}: {}", chapterId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Chapter> findExistingChapterByHash(String contentHash) {
        try {
            return contentPersistencePort.findChapterByContentHash(contentHash);
        } catch (Exception e) {
            log.warn("Graph lookup failed for content hash {}: {}", contentHash, e.getMessage());
            return Optional.empty();
        }
    }

    private UUID createNewChapter(SubmitChapterRequest request, String contentHash) {
        try {
            Chapter chapter = buildChapter(request, contentHash);
            Chapter persisted = contentPersistencePort.createChapter(chapter);
            
            // Handle mock scenarios where createChapter might return null
            UUID chapterId = (persisted != null) ? persisted.getId() : chapter.getId();
            
            log.debug("Created new chapter with ID: {}", chapterId);
            return chapterId;
            
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create chapter in graph: " + e.getMessage(), e);
        }
    }

    private Chapter buildChapter(SubmitChapterRequest request, String contentHash) {
        // Lookup book and derive hierarchy info
        Book book = contentPersistencePort.findBookById(request.getBookId())
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + request.getBookId()));

        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse(book.getUniverse());
        coords.setSeries(book.getSeries());
        coords.setBookTitle(book.getTitle());
        coords.setChapterTitle(request.getChapterTitle());
        coords.setBookNumber(book.getBookNumber() != null ? book.getBookNumber() : 0);
        coords.setChapterNumber(request.getChapterNumber());

        // Build Chapter with stable references
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setBookId(book.getId());
        chapter.setUniverseId(book.getUniverseId());
        chapter.setSeriesId(book.getSeriesId());
        chapter.setCoordinates(coords);
        chapter.setChapterTitle(request.getChapterTitle());
        chapter.setRawText(request.getChapterText());
        chapter.setContentHash(contentHash);
        return chapter;
    }

    // ===============================================
    // Workflow orchestration methods (former IngestionWorkflowService logic)
    // ===============================================

    /**
     * Context object for workflow processing state
     */
    public static class WorkflowContext {
        private final IngestionJob job;
        private final Chapter chapter;
        private final String chapterText;

        public WorkflowContext(IngestionJob job, Chapter chapter, String chapterText) {
            this.job = job;
            this.chapter = chapter;
            this.chapterText = chapterText;
        }

        public IngestionJob getJob() { return job; }
        public Chapter getChapter() { return chapter; }
        public String getChapterText() { return chapterText; }
        public UUID getChapterId() { return chapter.getId(); }
        public UUID getJobId() { return job.getId(); }
    }

    private List<Scene> executeSceneDetectionStage(WorkflowContext context) {
        updateStatus(context, IngestionStatus.DETECTING_SCENES, 
                "Analyzing chapter text with AI to identify semantic scene boundaries");

        // Check for existing scenes first
        List<Scene> existingScenes = contentPersistencePort.findScenesByChapterId(context.getChapterId());
        if (!existingScenes.isEmpty()) {
            updateStatus(context, IngestionStatus.DETECTING_SCENES, 
                    String.format("Found %d existing scenes, proceeding to chunking", existingScenes.size()));
            return existingScenes;
        }

        // Detect and persist new scenes using consolidated service
        List<Scene> scenes = sceneProcessingService
                .detectAndPersistScenes(context.getChapterId());
        
        // Create default temporal edges for the newly persisted scenes
        log.info("Creating default temporal edges for chapter {}", context.getChapterId());
        defaultTemporalEdgeService.createAllDefaults(context.getChapter().getBookId());
        
        updateStatus(context, IngestionStatus.DETECTING_SCENES, 
                String.format("Detected %d semantic scenes from chapter text", scenes.size()));
        
        return scenes;
    }

    private int executeChunkingStage(WorkflowContext context, List<Scene> scenes) {
        updateStatus(context, IngestionStatus.EMBEDDING_CHUNKS, 
                "Applying chunking decision gate to scenes");

        int chunkCount = createChunksFromScenes(context, scenes);
        
        updateStatus(context, IngestionStatus.EMBEDDING_CHUNKS, 
                String.format("Created %d chunks from %d semantic scenes", chunkCount, scenes.size()));
        
        return chunkCount;
    }

    private int executeEmbeddingStage(WorkflowContext context) {
        updateStatus(context, IngestionStatus.EMBEDDING_CHUNKS, 
                "Generating embeddings for chapter chunks");

        int embeddedCount = chunkEmbeddingService.generateEmbeddingsForChapter(context.getChapterId());
        
        log.info("Generated embeddings for {} chunks for chapter {}", 
                embeddedCount, context.getChapterId());
        
        return embeddedCount;
    }

    /**
     * Creates chunks from scenes following the text-chunking specification.
     * Implements Stage 3 (Chunking Decision Gate) and Stage 4 (Chunk Generation):
     * Uses TextChunkingService which transparently handles both single and multi-chunk cases.
     */
    private int createChunksFromScenes(WorkflowContext context, List<Scene> scenes) {
        if (context.getChapterText() == null) {
            return 0;
        }

        int totalChunks = 0;
        for (Scene scene : scenes) {
            totalChunks += processSceneIntoChunks(context, scene);
        }
        
        log.info("Created {} total chunks from {} scenes (scene-linked)", 
                totalChunks, scenes.size());
        return totalChunks;
    }

    private int processSceneIntoChunks(WorkflowContext context, Scene scene) {
        String sceneText = extractSceneText(context.getChapterText(), scene);
        List<Chunk> sceneChunks = textChunkingService.extractChunks(sceneText);
        
        List<Chunk> chunks = buildChunks(context, scene, sceneChunks);
        
        if (!chunks.isEmpty()) {
            contentPersistencePort.addChunksToScene(scene.getId(), chunks);
            return chunks.size();
        }
        
        return 0;
    }

    private String extractSceneText(String chapterText, Scene scene) {
        return chapterText.substring(
                scene.getStartCharacterOffset().intValue(), 
                scene.getEndCharacterOffset().intValue()
        );
    }

    private List<Chunk> buildChunks(WorkflowContext context, Scene scene, List<Chunk> chunks) {
        List<Chunk> chunkList = new ArrayList<>();
        
        for (Chunk chunk : chunks) {
            // Adjust chunk coordinates to chapter-relative positions
            chunk.setStartCharInChapter(chunk.getStartCharInChapter() + scene.getStartCharacterOffset().intValue());
            chunk.setEndCharInChapter(chunk.getEndCharInChapter() + scene.getStartCharacterOffset().intValue());
            
            Chunk newChunk = createChunk(context, chunk);
            chunkList.add(newChunk);
        }
        
        return chunkList;
    }

    private Chunk createChunk(WorkflowContext context, Chunk chunk) {
        // Use the normalized chunk text from TextChunkingService instead of raw chapter substring
        String chunkContent = chunk.getText(); // This contains the properly normalized text
        String contentHash = generateSha256Hash(chunkContent);
        
        Chunk newChunk = new Chunk();
        // Legacy: still populate for backward compatibility; will migrate to relationship ordering
        newChunk.setChunkNumberInChapter(chunk.getChunkNumberInChapter());
        newChunk.setStartCharInChapter(chunk.getStartCharInChapter());
        newChunk.setEndCharInChapter(chunk.getEndCharInChapter());
        newChunk.setContentHash(contentHash);
        newChunk.setText(chunkContent); // Store the normalized chunk text from TextChunkingService
        
        return newChunk;
    }

    private void handleProcessingError(WorkflowContext context, Exception e) {
        log.error("Error processing chapter {} for job {}: {}", 
                context.getChapterId(), context.getJobId(), e.getMessage(), e);

        if (isRetryableError(e)) {
            log.warn("LLM API failure detected - cleaning up data for retry");
            ingestionJobService.failJobWithCleanup(context.getJob(), 
                    "LLM API call failed: " + e.getMessage());
        } else {
            ingestionJobService.failJob(context.getJob(), 
                    "Chapter processing failed: " + e.getMessage());
        }
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("LLM API") || 
                message.contains("scene detection failed") || 
                message.contains("Empty response") || 
                message.contains("failed permanently after multiple attempts")
        );
    }

    private void updateStatus(WorkflowContext context, IngestionStatus status, String description) {
        ingestionJobService.updateJobStatus(context.getJobId(), status, description, Collections.emptyMap());
    }

    private int getChapterLength(String chapterText) {
        return chapterText != null ? chapterText.length() : 0;
    }
}
