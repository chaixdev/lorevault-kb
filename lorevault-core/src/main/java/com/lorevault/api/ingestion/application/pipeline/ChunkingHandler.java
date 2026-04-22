package com.lorevault.api.ingestion.application.pipeline;

import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.domain.IngestionStatus;

import com.lorevault.api.content.domain.Chapter;
import com.lorevault.api.content.domain.Chunk;
import com.lorevault.api.content.domain.Scene;
import com.lorevault.api.ingestion.events.ChunksCreatedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.content.infrastructure.ChapterGraphRepository;
import com.lorevault.api.content.infrastructure.ChunkGraphRepository;
import com.lorevault.api.content.infrastructure.SceneGraphRepository;
import com.lorevault.api.ai.application.TextChunkingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.lorevault.api.ingestion.infrastructure.HashUtils.generateSha256Hash;

/**
 * Handler for text chunking stage of the ingestion pipeline.
 * 
 * Listens to: ScenesDetectedEvent
 * Emits: ChunksCreatedEvent (on success) or IngestionFailedEvent (on failure)
 * 
 * Responsibilities:
 * - Break down scene text into embeddable chunks
 * - Apply overlap strategy for context preservation
 * - Persist chunks with scene relationships
 */
@Component
@Slf4j
public class ChunkingHandler {

    private final ChapterGraphRepository chapterRepo;
    private final ChunkGraphRepository chunkRepo;
    private final SceneGraphRepository sceneRepo;
    private final TextChunkingService textChunkingService;
    private final ApplicationEventPublisher eventPublisher;
    private final PipelineStageSupport stageSupport;

    public ChunkingHandler(
            ChapterGraphRepository chapterRepo,
            ChunkGraphRepository chunkRepo,
            SceneGraphRepository sceneRepo,
            TextChunkingService textChunkingService,
            IngestionJobService ingestionJobService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chapterRepo = chapterRepo;
        this.chunkRepo = chunkRepo;
        this.sceneRepo = sceneRepo;
        this.textChunkingService = textChunkingService;
        this.eventPublisher = eventPublisher;
        this.stageSupport = new PipelineStageSupport(ingestionJobService, eventPublisher);
    }

    @Async
    @EventListener
    public void handleScenesDetected(ScenesDetectedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();
        
        log.info("[CHUNKING] Starting for job={}, chapter={}, sceneCount={}", 
                jobId, chapterId, event.getSceneCount());
        
        stageSupport.runStage(
            this,
                "CHUNKING",
                jobId,
                chapterId,
                () -> {
            stageSupport.updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    "Breaking down scenes into embeddable text chunks");

            // Check for existing chunks (idempotency)
            boolean chunksExist = chunkRepo.existsForChapterViaScenes(chapterId) || chunkRepo.existsForChapter(chapterId);
            if (chunksExist) {
                int via = chunkRepo.countByChapterIdViaScenes(chapterId);
                int existingCount = via > 0 ? via : chunkRepo.countByChapterId(chapterId);
                log.info("[CHUNKING] Found {} existing chunks for chapter {}, skipping", 
                        existingCount, chapterId);
                emitChunksCreated(jobId, chapterId, bookId, existingCount);
                return null;
            }

            // Get chapter text for chunk extraction
            Chapter chapter = chapterRepo.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
            
            String chapterText = chapter.getRawText();
            if (chapterText == null || chapterText.isEmpty()) {
                log.warn("[CHUNKING] Chapter {} has no text content", chapterId);
                emitChunksCreated(jobId, chapterId, bookId, 0);
                return null;
            }

            // Get scenes and create chunks
            List<Scene> scenes = sceneRepo.findByChapterId(chapterId);
            int totalChunks = createChunksFromScenes(chapterText, scenes);
            
                stageSupport.updateJobStatus(jobId, IngestionStatus.EMBEDDING_CHUNKS,
                    String.format("Created %d chunks from %d scenes", totalChunks, scenes.size()));
            
            log.info("[CHUNKING] Completed for chapter {}: {} chunks from {} scenes", 
                    chapterId, totalChunks, scenes.size());
            
            emitChunksCreated(jobId, chapterId, bookId, totalChunks);

            return null;
                },
                e -> false
        );
    }

    private int createChunksFromScenes(String chapterText, List<Scene> scenes) {
        int totalChunks = 0;
        
        for (Scene scene : scenes) {
            totalChunks += processSceneIntoChunks(chapterText, scene);
        }
        
        log.debug("[CHUNKING] Created {} total chunks from {} scenes", totalChunks, scenes.size());
        return totalChunks;
    }

    private int processSceneIntoChunks(String chapterText, Scene scene) {
        String sceneText = extractSceneText(chapterText, scene);
        List<Chunk> rawChunks = textChunkingService.extractChunks(sceneText);
        
        List<Chunk> chunks = buildChunks(scene, rawChunks);
        
        if (!chunks.isEmpty()) {
            // Inline addChunksToScene logic: save each chunk and link to scene
            for (Chunk chunk : chunks) {
                if (chunk.getId() == null) {
                    chunk.setId(java.util.UUID.randomUUID());
                }
                Chunk saved = chunkRepo.save(chunk);
                sceneRepo.linkChunkToScene(scene.getId(), saved.getId(), chunk.getChunkNumberInChapter());
            }
            return chunks.size();
        }
        
        return 0;
    }

    private String extractSceneText(String chapterText, Scene scene) {
        int start = scene.getStartCharacterOffset().intValue();
        int end = scene.getEndCharacterOffset().intValue();
        
        // Bounds checking
        if (start < 0) start = 0;
        if (end > chapterText.length()) end = chapterText.length();
        if (start >= end) return "";
        
        return chapterText.substring(start, end);
    }

    private List<Chunk> buildChunks(Scene scene, List<Chunk> rawChunks) {
        List<Chunk> chunks = new ArrayList<>();
        int sceneStartOffset = scene.getStartCharacterOffset().intValue();
        
        for (Chunk rawChunk : rawChunks) {
            // Adjust coordinates to chapter-relative positions
            int startInChapter = rawChunk.getStartCharInChapter() + sceneStartOffset;
            int endInChapter = rawChunk.getEndCharInChapter() + sceneStartOffset;
            
            String chunkContent = rawChunk.getText();
            String contentHash = generateSha256Hash(chunkContent);
            
            Chunk chunk = new Chunk();
            chunk.setChunkNumberInChapter(rawChunk.getChunkNumberInChapter());
            chunk.setStartCharInChapter(startInChapter);
            chunk.setEndCharInChapter(endInChapter);
            chunk.setContentHash(contentHash);
            chunk.setText(chunkContent);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }

    private void emitChunksCreated(UUID jobId, UUID chapterId, UUID bookId, int chunkCount) {
        log.info("[CHUNKING] Emitting ChunksCreatedEvent: job={}, chapter={}, chunkCount={}", 
                jobId, chapterId, chunkCount);
        
        eventPublisher.publishEvent(new ChunksCreatedEvent(this, jobId, chapterId, bookId, chunkCount));
    }
}
