package com.lorevault.api.ai.application;

import com.lorevault.api.content.entities.Chapter;
import com.lorevault.api.content.entities.Chunk;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.content.entities.ChunkGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transactional DB operations for the embedding pipeline, extracted into a
 * separate bean so that the declarative proxy is honoured and external embedding
 * API calls can happen outside any transaction boundary.
 *
 * <p>Both methods are intentionally scoped: the read phase is read-only, the
 * persist phase is read-write.  {@link EmbeddingService} calls them with the
 * external API call occurring between the two, ensuring no DB connection is
 * held open during network I/O.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingTransactionSupport {

    private final ChapterGraphRepository chapterRepo;
    private final ChunkGraphRepository chunkRepo;

    /**
     * Load chunks for the chapter and return all of them.
     * Called before the external embedding API call.
     */
    @Transactional(readOnly = true)
    List<Chunk> loadChunks(UUID chapterId) {
        List<Chunk> viaScenes = chunkRepo.findByChapterIdViaScenes(chapterId);
        List<Chunk> chunks = !viaScenes.isEmpty() ? viaScenes : chunkRepo.findByChapterId(chapterId);
        log.debug("[Embeddings] Loaded {} chunks chapter={}", chunks.size(), chapterId);
        return chunks;
    }

    /**
     * Load the raw text of a chapter for coordinate-based text extraction.
     * Called before the external embedding API call.
     */
    @Transactional(readOnly = true)
    String loadChapterRawText(UUID chapterId) {
        try {
            return chapterRepo.findById(chapterId)
                    .map(Chapter::getRawText)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[Embeddings] Failed to load chapter rawText chapter={} error={}", chapterId, e.getMessage());
            return null;
        }
    }

    /**
     * Persist updated chunk embeddings after the external API call completes.
     * Called after the external embedding API call — no external I/O inside this transaction.
     */
    @Transactional
    void saveChunks(List<Chunk> chunks) {
        chunkRepo.saveAll(chunks);
    }
}
