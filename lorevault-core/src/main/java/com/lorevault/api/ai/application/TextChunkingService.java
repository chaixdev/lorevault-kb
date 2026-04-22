package com.lorevault.api.ai.application;

import com.lorevault.api.config.LoreVaultContentProperties;
import com.lorevault.api.content.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for deterministic text chunking implementing the LoreVault text-chunking specification.
 * Uses a decision gate approach: text ≤ threshold creates a single chunk,
 * text > threshold applies boundary-aware sliding window with overlap.
 */
@Service
@Slf4j
public class TextChunkingService {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?…]+[\"'”’)]*\\s+");
    private static final Pattern PARAGRAPH_BREAK_PATTERN = Pattern.compile("\\n\\s*\\n");
    private static final Pattern DIALOGUE_TURN_PATTERN = Pattern.compile("(?:\\n\\s*[\"“'‘-]|[\"“'‘][A-Z])");

    private static final int DEFAULT_DECISION_THRESHOLD = 1500;
    private static final int DEFAULT_TARGET_CHUNK_SIZE = 800;
    private static final int DEFAULT_MIN_CHUNK_SIZE = 400;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1200;
    private static final int DEFAULT_OVERLAP_PERCENTAGE = 25;

    private static final int PARAGRAPH_WEIGHT = 100;
    private static final int SENTENCE_WEIGHT = 40;
    private static final int DIALOGUE_WEIGHT = 25;
    private static final int DISTANCE_PENALTY_DIVISOR = 10;

    private final int decisionThreshold;
    private final int targetChunkSize;
    private final int minChunkSize;
    private final int maxChunkSize;
    private final int overlapSize;

    public TextChunkingService(LoreVaultContentProperties contentProperties) {
        LoreVaultContentProperties.ChunkingProperties chunking = contentProperties.chunking();
        Integer configuredTarget = chunking.targetSize();
        Integer configuredDecision = chunking.decisionThreshold();
        Integer configuredMin = chunking.minChunkSize();
        Integer configuredMax = chunking.maxChunkSize();
        Integer configuredOverlapPercentage = chunking.overlapPercentage();

        this.targetChunkSize = configuredTarget != null ? configuredTarget : DEFAULT_TARGET_CHUNK_SIZE;
        this.decisionThreshold = configuredDecision != null ? configuredDecision : DEFAULT_DECISION_THRESHOLD;
        this.minChunkSize = configuredMin != null ? configuredMin : DEFAULT_MIN_CHUNK_SIZE;
        this.maxChunkSize = configuredMax != null ? configuredMax : DEFAULT_MAX_CHUNK_SIZE;
        int overlapPercentage = configuredOverlapPercentage != null
            ? configuredOverlapPercentage
            : DEFAULT_OVERLAP_PERCENTAGE;
        this.overlapSize = Math.max(0, (int) Math.round(this.targetChunkSize * (overlapPercentage / 100.0)));
    }

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
     * Apply boundary-aware sliding window algorithm for multi-chunk subdivision.
     * Implements Stage 4B from the text-chunking specification.
     */
    private List<Chunk> applySentenceAwareSlidingWindow(String text) {
        List<Chunk> chunks = new ArrayList<>();
        List<BoundaryCandidate> boundaryCandidates = findBoundaryCandidates(text);
        
        int currentStart = 0;
        int chunkNumber = 1;
        
        while (currentStart < text.length()) {
            ChunkBoundary boundary = findOptimalChunkBoundary(text, currentStart, boundaryCandidates);
            
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
        return new Chunk(
            null,
            chunkNumber,
            startChar,
            endChar,
            null,
            content,
            null,
            null,
            null,
            null,
            null
        );
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
     * Find boundary candidates in descending preference order: paragraph, sentence, dialogue.
     */
    private List<BoundaryCandidate> findBoundaryCandidates(String text) {
        List<BoundaryCandidate> boundaries = new ArrayList<>();

        Matcher paragraphMatcher = PARAGRAPH_BREAK_PATTERN.matcher(text);
        while (paragraphMatcher.find()) {
            boundaries.add(new BoundaryCandidate(paragraphMatcher.end(), PARAGRAPH_WEIGHT, BoundaryType.PARAGRAPH));
        }

        Matcher sentenceMatcher = SENTENCE_PATTERN.matcher(text);
        while (sentenceMatcher.find()) {
            boundaries.add(new BoundaryCandidate(sentenceMatcher.end(), SENTENCE_WEIGHT, BoundaryType.SENTENCE));
        }

        Matcher dialogueMatcher = DIALOGUE_TURN_PATTERN.matcher(text);
        while (dialogueMatcher.find()) {
            boundaries.add(new BoundaryCandidate(dialogueMatcher.start(), DIALOGUE_WEIGHT, BoundaryType.DIALOGUE));
        }

        boundaries.add(new BoundaryCandidate(text.length(), Integer.MIN_VALUE, BoundaryType.END));
        return boundaries;
    }

    private record BoundaryCandidate(int position, int weight, BoundaryType type) {}

    private enum BoundaryType {
        PARAGRAPH,
        SENTENCE,
        DIALOGUE,
        END
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
     * Find optimal chunk boundary that respects detected boundaries and specification sizing.
     */
    private ChunkBoundary findOptimalChunkBoundary(String text, int start, List<BoundaryCandidate> candidates) {
        // Calculate target end position from start
        int targetEnd = start + targetChunkSize;
        
        // Find the best boundary near the target end
        int bestEnd = findBestBoundary(candidates, start, targetEnd, text.length());
        
        // Calculate next start position with overlap, but ensure progress
        int nextStart = Math.max(start + minChunkSize, bestEnd - overlapSize);
        
        return new ChunkBoundary(start, bestEnd, nextStart);
    }

    /**
     * Find the best boundary position that creates chunks within specification range.
     * Prefers stronger boundary types and chunks closer to target size.
     */
    private int findBestBoundary(List<BoundaryCandidate> candidates, int start, int targetEnd, int textLength) {
        int bestEnd = targetEnd;
        int bestScore = Integer.MIN_VALUE;
        
        for (BoundaryCandidate candidate : candidates) {
            int boundary = candidate.position();
            if (boundary <= start) continue;
            if (boundary > textLength) break;
            
            int chunkSize = boundary - start;
            
            // Only consider boundaries that create chunks within our size constraints
            if (chunkSize >= minChunkSize && chunkSize <= maxChunkSize) {
                int distance = Math.abs(boundary - targetEnd);
                int score = candidate.weight() - (distance / DISTANCE_PENALTY_DIVISOR);
                
                // Prefer stronger boundary types and closer distances; break ties with larger chunk
                if (score > bestScore || 
                    (score == bestScore && chunkSize > (bestEnd - start))) {
                    bestEnd = boundary;
                    bestScore = score;
                }
            }
        }
        
        // If no suitable boundary found, use target end but ensure minimum size
        if (bestEnd == targetEnd && (targetEnd - start) < minChunkSize) {
            // Find the first boundary that gives us minimum size
            for (BoundaryCandidate candidate : candidates) {
                int boundary = candidate.position();
                if (boundary > start && (boundary - start) >= minChunkSize) {
                    bestEnd = Math.min(boundary, textLength);
                    break;
                }
            }
        }
        
        // Ensure we don't exceed text length
        return Math.min(bestEnd, textLength);
    }
}
