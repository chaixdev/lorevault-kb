package com.lorevault.api.ai;

import com.lorevault.api.content.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for deterministic text chunking implementing the LoreVault text-chunking specification.
 * Uses a decision gate approach: text ≤ 5000 chars creates a single chunk,
 * text > 5000 chars applies sentence-aware sliding window with 15% overlap.
 * Implements overlapping chunks to prevent information loss at boundaries.
 */
@Service
@Slf4j
public class TextChunkingService {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");
    
    @Value("${lorevault.chunking.decision-threshold:5000}")
    private int decisionThreshold;
    
    @Value("${lorevault.chunking.target-size:3000}")
    private int targetChunkSize;
    
    @Value("${lorevault.chunking.overlap-percentage:15}")
    private int overlapPercentage;
    
    @Value("${lorevault.chunking.min-chunk-size:2000}")
    private int minChunkSize;
    
    @Value("${lorevault.chunking.max-chunk-size:4000}")
    private int maxChunkSize;

    /**
     * Split text into overlapping chunks using specification-compliant decision gate approach.
     * Implements Stage 3 & 4 from text-chunking-specification.md:
     * - Stage 3: Decision gate based on text length threshold
     * - Stage 4A: Single chunk creation for text ≤ threshold
     * - Stage 4B: Sentence-aware sliding window for text > threshold
     * 
     * @param text The text to chunk
     * @return List of chunks with position coordinates
     */
    public List<Chunk> extractChunks(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        text = normalizeText(text);
        
        // Stage 3: Chunking Decision Gate
        if (text.length() <= decisionThreshold) {
            // Stage 4A: Single-Chunk Creation
            log.debug("Text length {} ≤ threshold {}, creating single chunk", text.length(), decisionThreshold);
            Chunk singleChunk = createChunk(1, 0, text.length(), text);
            log.info("Single chunk created: {} chars", text.length());
            return List.of(singleChunk);
        }
        
        // Stage 4B: Multi-Chunk Subdivision with Sentence-Aware Sliding Window
        log.debug("Text length {} > threshold {}, applying sliding window", text.length(), decisionThreshold);
        return applySentenceAwareSlidingWindow(text);
    }

    /**
     * Apply sentence-aware sliding window algorithm for multi-chunk subdivision.
     * Implements Stage 4B from the text-chunking specification.
     */
    private List<Chunk> applySentenceAwareSlidingWindow(String text) {
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
            
            // Check if the remaining text after this chunk is too small for another chunk
            int remainingAfterNext = text.length() - boundary.nextStart;
            if (remainingAfterNext < minChunkSize) {
                // Extend the current chunk to include the remaining text
                chunks.removeLast(); // Remove the last chunk
                chunk = createChunk(chunkNumber, boundary.startChar, text.length(), 
                                  text.substring(boundary.startChar));
                chunks.add(chunk);
                log.debug("Extended final chunk {}: chars {}-{} (length: {})",
                        chunkNumber, boundary.startChar, text.length(), 
                        text.length() - boundary.startChar);
                break;
            }
            
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
        chunk.setText(content); // Store the actual chunk text for embedding independence
        return chunk;
    }

    /**
     * Normalize text for consistent chunking
     */
    private String normalizeText(String text) {
        return text.trim()
                   .replaceAll("\\r\\n", "\n")          // Normalize line endings 
                   .replaceAll("\\r", "\n")             // Handle old Mac line endings
                   .replaceAll("\\n{3,}", "\n\n")       // Collapse 3+ newlines to 2 (preserve paragraphs)
                   .replaceAll("[ \\t]+", " ")          // Collapse spaces/tabs only (preserve newlines)
                   .replaceAll("\\n ", "\n")            // Remove spaces after newlines  
                   .replaceAll(" \\n", "\n");           // Remove spaces before newlines
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
     * Find optimal chunk boundary that respects sentence boundaries and specification sizing.
     * Creates chunks in the 2000-4000 character range with 15% sentence-aware overlap.
     */
    private ChunkBoundary findOptimalChunkBoundary(String text, int start, List<Integer> sentenceEnds) {
        // Calculate target end position from start
        int targetEnd = start + targetChunkSize;
        
        // Find the best sentence boundary near the target end
        int bestEnd = findBestSentenceEnd(sentenceEnds, start, targetEnd, text.length());
        
        // Calculate overlap size for next chunk start
        int overlapSize = (int) (targetChunkSize * (overlapPercentage / 100.0));
        
        // Calculate next start position with overlap, but ensure progress
        int nextStart = Math.max(start + minChunkSize, bestEnd - overlapSize);
        
        return new ChunkBoundary(start, bestEnd, nextStart);
    }

    /**
     * Find the best sentence ending position that creates chunks within specification range.
     * Prefers chunks closer to target size while respecting sentence boundaries.
     */
    private int findBestSentenceEnd(List<Integer> sentenceEnds, int start, int targetEnd, int textLength) {
        int bestEnd = targetEnd;
        int minDistance = Integer.MAX_VALUE;
        
        for (int sentenceEnd : sentenceEnds) {
            if (sentenceEnd <= start) continue;
            if (sentenceEnd > textLength) break;
            
            int chunkSize = sentenceEnd - start;
            
            // Only consider sentence ends that create chunks within our size constraints
            if (chunkSize >= minChunkSize && chunkSize <= maxChunkSize) {
                int distance = Math.abs(sentenceEnd - targetEnd);
                
                // Prefer larger chunks (closer to target) when distances are equal
                if (distance < minDistance || 
                    (distance == minDistance && chunkSize > (bestEnd - start))) {
                    bestEnd = sentenceEnd;
                    minDistance = distance;
                }
            }
        }
        
        // If no suitable sentence boundary found, use target end but ensure minimum size
        if (bestEnd == targetEnd && (targetEnd - start) < minChunkSize) {
            // Find the first sentence end that gives us minimum size
            for (int sentenceEnd : sentenceEnds) {
                if (sentenceEnd > start && (sentenceEnd - start) >= minChunkSize) {
                    bestEnd = Math.min(sentenceEnd, textLength);
                    break;
                }
            }
        }
        
        // Ensure we don't exceed text length
        return Math.min(bestEnd, textLength);
    }
}
