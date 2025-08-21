package com.lorevault.api.infrastructure.search;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.SemanticSearchPort;
import com.lorevault.api.domain.content.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * In-memory semantic search implementation using cosine similarity.
 * Loads chunks with embeddings from database and performs vector similarity in memory.
 * This is a v0.7.0 implementation that will be replaced with database-native vector search.
 * 
 * Activated when lorevault.search.provider=memory or as fallback when Neo4j unavailable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "lorevault.search.provider", havingValue = "memory", matchIfMissing = false)
public class InMemorySemanticSearchAdapter implements SemanticSearchPort {

    private final ContentPersistencePort contentPersistencePort;
    @org.springframework.beans.factory.annotation.Value("${lorevault.search.snippet.max-length:600}")
    private int maxSnippetLength;

    @Override
    public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters) {
        log.debug("Performing in-memory semantic search with topK: {} and filters: {}", topK, filters);
        
        long startTime = System.currentTimeMillis();
        
        // Load all chunks with embeddings
        // TODO: In future versions, this should be optimized with database-native vector search
        List<Chunk> chunks = loadChunksWithEmbeddings();
        log.debug("Loaded {} chunks with embeddings", chunks.size());
        
        if (chunks.isEmpty()) {
            return List.of();
        }

        // Filter chunks based on search filters
        Stream<Chunk> filteredChunks = applyFilters(chunks.stream(), filters);

        // Calculate cosine similarity and rank results
        List<SearchResult> results = filteredChunks
            .map(chunk -> calculateSimilarity(chunk, queryEmbedding))
            .filter(result -> result.score() > 0.0) // Filter out negative similarities
            .sorted(Comparator.comparing(SearchResult::score).reversed())
            .limit(topK)
            .toList();

        long processingTime = System.currentTimeMillis() - startTime;
        log.debug("In-memory search completed in {}ms, returning {} results", processingTime, results.size());

        return results;
    }

    @Override
    public boolean isAvailable() {
        // Check if any chunks have embeddings
        List<Chunk> chunks = loadChunksWithEmbeddings();
        boolean available = !chunks.isEmpty();
        log.debug("Semantic search available: {} (found {} chunks with embeddings)", available, chunks.size());
        return available;
    }

    private List<Chunk> loadChunksWithEmbeddings() {
        // Load all chunks from database that have embeddings
        // This is a placeholder implementation - in practice we'd need a method to fetch all embedded chunks
        // For now, we'll fetch chunks from all chapters and filter for those with embeddings
        try {
            return contentPersistencePort.findAllChunksWithEmbeddings();
        } catch (Exception e) {
            log.warn("Failed to load chunks with embeddings, falling back to empty list: {}", e.getMessage());
            return List.of();
        }
    }

    private Stream<Chunk> applyFilters(Stream<Chunk> chunks, SearchFilters filters) {
        if (!filters.hasFilters()) {
            return chunks;
        }

        return chunks.filter(chunk -> matchesFilters(chunk, filters));
    }

    private boolean matchesFilters(Chunk chunk, SearchFilters filters) {
        // TODO: Implement filtering based on chunk metadata
        // For v0.7.0, we'll keep this simple and not implement complex filtering
        // This would require navigation to chapter -> book -> series -> universe
        
        // For now, we'll always match since we don't have easy access to hierarchy metadata on chunks
        // In future versions, materialized coordinates on chunks would make this efficient
        return true;
    }

    private SearchResult calculateSimilarity(Chunk chunk, double[] queryEmbedding) {
        double[] chunkEmbedding = chunk.getEmbedding();
        
        if (chunkEmbedding == null || chunkEmbedding.length != queryEmbedding.length) {
            return new SearchResult(chunk.getId(), 0.0, truncateSnippet(chunk.getText()), 
                                  null, null, null);
        }

        double similarity = cosineSimilarity(queryEmbedding, chunkEmbedding);
        
        return new SearchResult(
            chunk.getId(),
            similarity,
            truncateSnippet(chunk.getText()),
            null, // chapterId - would need to traverse relationships  
            null, // bookNumber - would need hierarchy traversal
            null  // chapterNumber - would need hierarchy traversal
        );
    }

    private double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String truncateSnippet(String text) {
        if (text == null) {
            return null;
        }
        
        // Create a snippet of reasonable length for display
        int maxLength = Math.max(50, maxSnippetLength);
        if (text.length() <= maxLength) {
            return text;
        }
        
        // Find a good break point near the limit
        int breakPoint = text.lastIndexOf(' ', maxLength - 3);
        if (breakPoint < maxLength / 2) {
            breakPoint = maxLength - 3;
        }
        
        return text.substring(0, breakPoint) + "...";
    }
}
