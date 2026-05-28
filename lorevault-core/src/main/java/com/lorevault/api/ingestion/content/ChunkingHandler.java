package com.lorevault.api.ingestion.content;

import com.lorevault.api.ingestion.pipeline.DispatchContext;
import com.lorevault.api.ingestion.pipeline.ForStage;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chunk.Chunk;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ai.chunking.TextChunkingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;
import static com.lorevault.api.ingestion.infrastructure.HashUtils.generateSha256Hash;

/**
 * Handler for text chunking stage of the ingestion pipeline.
 *
 * Listens to: StageTriggeredEvent (CHUNKING)
 * Emits: StageCompletedEvent (on success, skip, or failure)
 *
 * Implements {@link ChunkingOperation} so the step-by-step execution controller or step-execution
 * endpoints can invoke chunking directly without Spring event dispatch.
 *
 * Responsibilities:
 * - Break down scene text into embeddable chunks
 * - Apply overlap strategy for context preservation
 * - Persist chunks with scene relationships
 */
@Component
@Slf4j
@ForStage(StageKey.CHUNKING)
public class ChunkingHandler implements ChunkingOperation {

    private final ChapterGraphRepository chapterRepo;
    private final ChunkGraphRepository chunkRepo;
    private final SceneGraphRepository sceneRepo;
    private final TextChunkingService textChunkingService;

    public ChunkingHandler(
            ChapterGraphRepository chapterRepo,
            ChunkGraphRepository chunkRepo,
            SceneGraphRepository sceneRepo,
            TextChunkingService textChunkingService
    ) {
        this.chapterRepo = chapterRepo;
        this.chunkRepo = chunkRepo;
        this.sceneRepo = sceneRepo;
        this.textChunkingService = textChunkingService;
    }

    @Override
    public StepResult execute(DispatchContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        long start = System.currentTimeMillis();
        try {
            // Check for existing chunks (idempotency)
            boolean chunksExist = chunkRepo.existsForChapterViaScenes(chapterId) || chunkRepo.existsForChapter(chapterId);
            if (chunksExist) {
                int via = chunkRepo.countByChapterIdViaScenes(chapterId);
                int existingCount = via > 0 ? via : chunkRepo.countByChapterId(chapterId);
                log.info("[CHUNKING] Found {} existing chunks for chapter {}, skipping", existingCount, chapterId);
                long elapsed = System.currentTimeMillis() - start;
                return StepResult.success(StageKey.CHUNKING,
                        String.format("Skipped — %d chunks already exist", existingCount),
                        Map.of("chunksCreated", existingCount),
                        elapsed);
            }

            // Get chapter text for chunk extraction
            Chapter chapter = chapterRepo.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

            String chapterText = chapter.getRawText();
            if (chapterText == null || chapterText.isEmpty()) {
                log.warn("[CHUNKING] Chapter {} has no text content", chapterId);
                long elapsed = System.currentTimeMillis() - start;
                return StepResult.success(StageKey.CHUNKING, "No text content — 0 chunks created",
                        Map.of("chunksCreated", 0), elapsed);
            }

            // Get scenes and create chunks
            List<Scene> scenes = sceneRepo.findByChapterId(chapterId);
            int totalChunks = createChunksFromScenes(chapterText, scenes);

            long elapsed = System.currentTimeMillis() - start;
            return StepResult.success(StageKey.CHUNKING,
                    String.format("Created %d chunks from %d scenes", totalChunks, scenes.size()),
                    Map.of("chunksCreated", totalChunks),
                    elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CHUNKING] Failed for job={} chapter={}: {}", jobId, chapterId, e.getMessage(), e);
            return StepResult.failure(StageKey.CHUNKING, sanitizeMessage(e), elapsed);
        }
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

}
