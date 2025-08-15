package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.service.content.ChunkEmbeddingService;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.ScenePersistenceService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.shared.HashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for orchestrating the ingestion workflow.
 * Handles the four-stage processing pipeline: Scene Detection → Coordinate Localization → 
 * Chunking Decision Gate → Chunk Generation and Embedding.
 * Extracted from IngestionService to improve single responsibility and testability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionWorkflowService {

    private final ContentPersistencePort contentPersistencePort;
    private final SceneDetectionService sceneDetectionService;
    private final ScenePersistenceService scenePersistenceService;
    private final TextChunkingService textChunkingService;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final HashService hashService;
    private final IngestionJobLifecycleService jobLifecycleService;

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

    /**
     * Process a chapter by detecting scenes and creating chunks following
     * the v0.3.0 text-chunking specification.
     * Implements the four-stage workflow: Scene Identification → Coordinate Localization →
     * Chunking Decision Gate → Chunk Generation
     */
    @Transactional
    public void processChapter(IngestionJob job, Chapter chapter) {
        WorkflowContext context = new WorkflowContext(job, chapter, chapter.getRawText());
        
        try {
            log.info("Starting v0.3.0 chapter processing for job {} and chapter {}", 
                    context.getJobId(), context.getChapterId());
            
            updateStatus(context, IngestionStatus.PREPROCESSING_STARTED, 
                    "Starting AI-powered scene detection");

            List<SceneNode> sceneNodes = executeSceneDetectionStage(context);
            executeChunkingStage(context, sceneNodes);
            executeEmbeddingStage(context);

            jobLifecycleService.completeJob(job, context.getChapterId(), 
                    getChapterLength(context.getChapterText()));
            
        } catch (Exception e) {
            handleProcessingError(context, e);
        }
    }

    private List<SceneNode> executeSceneDetectionStage(WorkflowContext context) {
        updateStatus(context, IngestionStatus.DETECTING_SCENES, 
                "Analyzing chapter text with AI to identify semantic scene boundaries");

        // Check for existing scenes first
        List<SceneNode> existingScenes = contentPersistencePort.findScenesByChapterId(context.getChapterId());
        if (!existingScenes.isEmpty()) {
            updateStatus(context, IngestionStatus.DETECTING_SCENES, 
                    String.format("Found %d existing scenes, proceeding to chunking", existingScenes.size()));
            return existingScenes;
        }

        // Detect new scenes
        List<SceneWithCoordinates> scenesWithCoordinates = sceneDetectionService
                .detectScenesForChapter(context.getChapterId());
        
        List<SceneNode> sceneNodes = scenePersistenceService
                .persistDetectedScenes(context.getChapterId(), scenesWithCoordinates);
        
        updateStatus(context, IngestionStatus.DETECTING_SCENES, 
                String.format("Detected %d semantic scenes from chapter text", sceneNodes.size()));
        
        return sceneNodes;
    }

    private int executeChunkingStage(WorkflowContext context, List<SceneNode> sceneNodes) {
        updateStatus(context, IngestionStatus.EMBEDDING_CHUNKS, 
                "Applying chunking decision gate to scenes");

        int chunkCount = createChunksFromScenes(context, sceneNodes);
        
        updateStatus(context, IngestionStatus.EMBEDDING_CHUNKS, 
                String.format("Created %d chunks from %d semantic scenes", chunkCount, sceneNodes.size()));
        
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
    private int createChunksFromScenes(WorkflowContext context, List<SceneNode> sceneNodes) {
        if (context.getChapterText() == null) {
            return 0;
        }

        int totalChunks = 0;
        for (SceneNode scene : sceneNodes) {
            totalChunks += processSceneIntoChunks(context, scene);
        }
        
        log.info("Created {} total chunks from {} scenes (scene-linked)", 
                totalChunks, sceneNodes.size());
        return totalChunks;
    }

    private int processSceneIntoChunks(WorkflowContext context, SceneNode scene) {
        String sceneText = extractSceneText(context.getChapterText(), scene);
        List<Chunk> sceneChunks = textChunkingService.extractChunks(sceneText);
        
        List<ChunkNode> chunkNodes = buildChunkNodes(context, scene, sceneChunks);
        
        if (!chunkNodes.isEmpty()) {
            contentPersistencePort.addChunksToScene(scene.getId(), chunkNodes);
            return chunkNodes.size();
        }
        
        return 0;
    }

    private String extractSceneText(String chapterText, SceneNode scene) {
        return chapterText.substring(
                scene.getStartOffset().intValue(), 
                scene.getEndOffset().intValue()
        );
    }

    private List<ChunkNode> buildChunkNodes(WorkflowContext context, SceneNode scene, List<Chunk> chunks) {
        List<ChunkNode> chunkNodes = new ArrayList<>();
        
        for (Chunk chunk : chunks) {
            // Adjust chunk coordinates to chapter-relative positions
            chunk.setStartCharInChapter(chunk.getStartCharInChapter() + scene.getStartOffset().intValue());
            chunk.setEndCharInChapter(chunk.getEndCharInChapter() + scene.getStartOffset().intValue());
            
            ChunkNode node = createChunkNode(context, chunk);
            chunkNodes.add(node);
        }
        
        return chunkNodes;
    }

    private ChunkNode createChunkNode(WorkflowContext context, Chunk chunk) {
        String chunkContent = context.getChapterText().substring(
                chunk.getStartCharInChapter(), 
                chunk.getEndCharInChapter()
        );
        String contentHash = hashService.generateSha256Hash(chunkContent);
        
        ChunkNode node = new ChunkNode();
        // Legacy: still populate for backward compatibility; will migrate to relationship ordering
        node.setChunkNumberInChapter(chunk.getChunkNumberInChapter());
        node.setStartCharInChapter(chunk.getStartCharInChapter());
        node.setEndCharInChapter(chunk.getEndCharInChapter());
        node.setContentHash(contentHash);
        node.setText(chunkContent); // Store the actual chunk text for debugging
        
        return node;
    }

    private void handleProcessingError(WorkflowContext context, Exception e) {
        log.error("Error processing chapter {} for job {}: {}", 
                context.getChapterId(), context.getJobId(), e.getMessage(), e);

        if (isRetryableError(e)) {
            log.warn("LLM API failure detected - cleaning up data for retry");
            jobLifecycleService.failJobWithCleanup(context.getJob(), 
                    "LLM API call failed: " + e.getMessage());
        } else {
            jobLifecycleService.failJob(context.getJob(), 
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
        jobLifecycleService.updateJobStatus(context.getJobId(), status, description, Collections.emptyMap());
    }

    private int getChapterLength(String chapterText) {
        return chapterText != null ? chapterText.length() : 0;
    }
}
