package com.lorevault.api.service;

import com.lorevault.api.model.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for deterministic text chunking using rolling window approach.
 * Implements overlapping chunks to prevent information loss at boundaries.
 */
@Service
@Slf4j
public class TextChunkingService {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");
    
    @Value("${lorevault.chunking.target-size:900}")
    private int targetChunkSize;
    
    @Value("${lorevault.chunking.overlap-percentage:30}")
    private int overlapPercentage;
    
    @Value("${lorevault.chunking.min-chunk-size:200}")
    private int minChunkSize;
    
    @Value("${lorevault.chunking.max-chunk-size:1200}")
    private int maxChunkSize;

    /**
     * Split text into overlapping chunks using rolling window approach.
     * Returns chunks with position information but no parent relationship set.
     * 
     * @param text The text to chunk
     * @return List of chunks with position coordinates
     */
    public List<Chunk> extractChunks(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        text = normalizeText(text);
        
        if (text.length() <= maxChunkSize) {
            // Single chunk for short text
            Chunk singleChunk = createChunk(1, 0, text.length(), text);
            return List.of(singleChunk);
        }

        List<Chunk> chunks = new ArrayList<>();
        List<Integer> sentenceEnds = findSentenceEnds(text);
        
        int currentStart = 0;
        int chunkNumber = 1;
        
        while (currentStart < text.length()) {
            ChunkBoundary boundary = findOptimalChunkBoundary(text, currentStart, sentenceEnds);
            
            String chunkContent = text.substring(boundary.startChar, boundary.endChar);
            Chunk chunk = createChunk(chunkNumber, boundary.startChar, boundary.endChar, chunkContent);
            chunks.add(chunk);
            
            log.debug("Created chunk {}: chars {}-{} (length: {})",
                    chunkNumber, boundary.startChar, boundary.endChar, chunkContent.length());
            
            // Move to next position with overlap
            currentStart = boundary.nextStart;
            chunkNumber++;
            
            // Safety check to prevent infinite loops
            if (boundary.nextStart <= boundary.startChar) {
                log.warn("Chunking algorithm detected potential infinite loop, breaking");
                break;
            }
        }

        log.info("Text chunking completed: {} chars -> {} chunks", text.length(), chunks.size());
        return chunks;
    }

    /**
     * Create a chunk entity with the given parameters
     */
    private Chunk createChunk(int chunkNumber, int startChar, int endChar, String content) {
        Chunk chunk = new Chunk();
        // Note: chapterId will be set by the calling service
        chunk.setChunkNumberInChapter(chunkNumber);
        chunk.setStartCharInChapter(startChar);
        chunk.setEndCharInChapter(endChar);
        return chunk;
    }

    /**
     * Normalize text for consistent chunking
     */
    private String normalizeText(String text) {
        return text.trim()
                   .replaceAll("\\r\\n", "\n")  // Normalize line endings
                   .replaceAll("\\r", "\n")     // Handle old Mac line endings
                   .replaceAll("\\s+", " ")     // Collapse multiple spaces
                   .replaceAll("\\n\\s*\\n", "\n\n"); // Preserve paragraph breaks
    }

    /**
     * Find sentence ending positions in the text
     */
    private List<Integer> findSentenceEnds(String text) {
        List<Integer> sentenceEnds = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            sentenceEnds.add(matcher.end());
        }
        
        // Add text end as final sentence boundary
        sentenceEnds.add(text.length());
        
        return sentenceEnds;
    }

    private static class ChunkBoundary {
        final int startChar;
        final int endChar;
        final int nextStart;

        ChunkBoundary(int startChar, int endChar, int nextStart) {
            this.startChar = startChar;
            this.endChar = endChar;
            this.nextStart = nextStart;
        }
    }

    /**
     * Find optimal chunk boundary that respects sentence boundaries and target size
     */
    private ChunkBoundary findOptimalChunkBoundary(String text, int start, List<Integer> sentenceEnds) {
        int overlapSize = (int) (targetChunkSize * (overlapPercentage / 100.0));
        int actualStart = Math.max(0, start - overlapSize);
        
        // Find target end position
        int targetEnd = actualStart + targetChunkSize;
        
        // Find the best sentence boundary near the target
        int bestEnd = findBestSentenceEnd(sentenceEnds, actualStart, targetEnd, text.length());
        
        // Calculate next start position (with overlap)
        int nextStart = Math.max(start + minChunkSize, bestEnd - overlapSize);
        
        return new ChunkBoundary(actualStart, bestEnd, nextStart);
    }

    /**
     * Find the best sentence ending position near the target
     */
    private int findBestSentenceEnd(List<Integer> sentenceEnds, int start, int targetEnd, int textLength) {
        // Find sentence ends within our range
        int bestEnd = targetEnd;
        int minDistance = Integer.MAX_VALUE;
        
        for (int sentenceEnd : sentenceEnds) {
            if (sentenceEnd <= start) continue;
            if (sentenceEnd > textLength) break;
            
            // Prefer sentence ends close to target, but not too short
            int distance = Math.abs(sentenceEnd - targetEnd);
            int chunkSize = sentenceEnd - start;
            
            if (chunkSize >= minChunkSize && chunkSize <= maxChunkSize && distance < minDistance) {
                bestEnd = sentenceEnd;
                minDistance = distance;
            }
        }
        
        // Ensure we don't exceed text length
        return Math.min(bestEnd, textLength);
    }
}
