package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.SemanticSearchPort;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fake semantic search port for deterministic test search results.
 * Maintains an in-memory index of chunks with deterministic similarity scoring.
 */
public final class FakeSemanticSearchPort implements SemanticSearchPort {
    
    private final Map<UUID, SearchResult> chunks = new HashMap<>();
    private boolean isAvailable = true;
    
    public FakeSemanticSearchPort() {}
    
    /**
     * Add a chunk to the searchable index.
     */
    public void addChunk(UUID chunkId, String text, UUID chapterId, Integer bookNumber, Integer chapterNumber) {
        chunks.put(chunkId, new SearchResult(
            chunkId, 
            0.0, // score will be calculated during search
            text.length() > 100 ? text.substring(0, 100) + "..." : text,
            chapterId,
            bookNumber,
            chapterNumber
        ));
    }
    
    /**
     * Clear all chunks from the index.
     */
    public void clear() {
        chunks.clear();
    }
    
    /**
     * Set availability status.
     */
    public void setAvailable(boolean available) {
        this.isAvailable = available;
    }
    
    @Override
    public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters) {
        if (!isAvailable || chunks.isEmpty()) {
            return List.of();
        }
        
        String queryHash = hashVector(queryEmbedding);
        
        List<SearchResult> results = new ArrayList<>();
        for (SearchResult chunk : chunks.values()) {
            // Apply filters if specified
            if (filters != null && filters.hasFilters()) {
                if (filters.universe() != null || filters.series() != null) {
                    // For simplicity, we'll skip filtering by universe/series in fake
                    // In real implementation, this would check materialized coordinates
                }
                if (filters.bookNumber() != null && !filters.bookNumber().equals(chunk.bookNumber())) {
                    continue;
                }
                if (filters.chapterNumber() != null && !filters.chapterNumber().equals(chunk.chapterNumber())) {
                    continue;
                }
            }
            
            // Calculate deterministic similarity score
            double score = calculateSimilarity(queryHash, chunk.snippet());
            if (score > 0.1) { // threshold to filter out very low scores
                results.add(new SearchResult(
                    chunk.chunkId(),
                    score,
                    chunk.snippet(),
                    chunk.chapterId(),
                    chunk.bookNumber(),
                    chunk.chapterNumber()
                ));
            }
        }
        
        // Sort by score descending and limit results
        return results.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }
    
    @Override
    public boolean isAvailable() {
        return isAvailable && !chunks.isEmpty();
    }
    
    /**
     * Calculate deterministic similarity between query hash and chunk text.
     */
    private double calculateSimilarity(String queryHash, String chunkText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String chunkHash = bytesToHex(digest.digest(chunkText.getBytes()));
            
            // Simple deterministic similarity: count matching characters at same positions
            int matches = 0;
            int length = Math.min(queryHash.length(), chunkHash.length());
            for (int i = 0; i < length; i++) {
                if (queryHash.charAt(i) == chunkHash.charAt(i)) {
                    matches++;
                }
            }
            
            // Normalize to [0, 1] range
            return (double) matches / Math.max(queryHash.length(), chunkHash.length());
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    private String hashVector(double[] vector) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (double d : vector) {
                sb.append(d).append(",");
            }
            return bytesToHex(digest.digest(sb.toString().getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
