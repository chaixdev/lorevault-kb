package com.lorevault.api.service;

import com.lorevault.api.model.Chapter;
import com.lorevault.api.model.Chunk;
import com.lorevault.api.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing Chunk entities and their relationships with Chapters
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkService {

    private final ChunkRepository chunkRepository;
    private final TextChunkingService textChunkingService;
    private final HashService hashService;

    /**
     * Create chunks for a chapter using rolling window text segmentation
     */
    @Transactional
    public List<Chunk> createChunksForChapter(Chapter chapter) {
        if (chapter == null || chapter.getRawText() == null) {
            throw new IllegalArgumentException("Chapter and rawText cannot be null");
        }

        log.info("Creating chunks for chapter: {} (length: {} chars)", 
                chapter.getId(), chapter.getRawText().length());

        // Check if chunks already exist for this chapter
        if (chunkRepository.existsByChapterId(chapter.getId())) {
            log.info("Chunks already exist for chapter {}, returning existing chunks", chapter.getId());
            return chunkRepository.findByChapterIdOrderByChunkNumber(chapter.getId());
        }

        // Perform text chunking using the simplified service
        List<Chunk> chunks = textChunkingService.extractChunks(chapter.getRawText());
        
        if (chunks.isEmpty()) {
            log.warn("No chunks generated for chapter {}", chapter.getId());
            return List.of();
        }

        // Set chapter relationship and content hashes
        for (Chunk chunk : chunks) {
            chunk.setChapterId(chapter.getId());
            
            // Extract content for this chunk and generate hash
            String chunkContent = chapter.getRawText().substring(
                chunk.getStartCharInChapter(), 
                chunk.getEndCharInChapter()
            );
            chunk.setContentHash(hashService.generateSha256Hash(chunkContent));
        }

        // Save all chunks
        List<Chunk> savedChunks = chunkRepository.saveAll(chunks);
        
        log.info("Created {} chunks for chapter {}", savedChunks.size(), chapter.getId());
        
        // Log chunk statistics
        logChunkStatistics(savedChunks, chapter);
        
        return savedChunks;
    }

    /**
     * Get all chunks for a chapter
     */
    public List<Chunk> getChunksForChapter(UUID chapterId) {
        return chunkRepository.findByChapterIdOrderByChunkNumber(chapterId);
    }

    /**
     * Get chunk count for a chapter
     */
    public int getChunkCount(UUID chapterId) {
        return chunkRepository.countByChapterId(chapterId);
    }

    /**
     * Delete all chunks for a chapter
     */
    @Transactional
    public void deleteChunksForChapter(UUID chapterId) {
        log.info("Deleting all chunks for chapter: {}", chapterId);
        chunkRepository.deleteByChapterId(chapterId);
    }

    /**
     * Check if chunks exist for a chapter
     */
    public boolean chunksExistForChapter(UUID chapterId) {
        return chunkRepository.existsByChapterId(chapterId);
    }

    /**
     * Recreate chunks for a chapter (delete existing and create new ones)
     */
    @Transactional
    public List<Chunk> recreateChunksForChapter(Chapter chapter) {
        log.info("Recreating chunks for chapter: {}", chapter.getId());
        
        // Delete existing chunks
        deleteChunksForChapter(chapter.getId());
        
        // Create new chunks
        return createChunksForChapter(chapter);
    }

    /**
     * Log statistics about the created chunks
     */
    private void logChunkStatistics(List<Chunk> chunks, Chapter chapter) {
        if (chunks.isEmpty()) {
            return;
        }

        int totalChunks = chunks.size();
        int totalChunkChars = chunks.stream().mapToInt(Chunk::getLength).sum();
        
        int minChunkSize = chunks.stream().mapToInt(Chunk::getLength).min().orElse(0);
        int maxChunkSize = chunks.stream().mapToInt(Chunk::getLength).max().orElse(0);
        double avgChunkSize = chunks.stream().mapToInt(Chunk::getLength).average().orElse(0);
        
        log.info("Chunk statistics for chapter {}: {} chunks, {} total chars, {}-{} chars per chunk (avg: {:.1f})", 
                chapter.getId(), totalChunks, totalChunkChars, minChunkSize, maxChunkSize, avgChunkSize);
    }
}
