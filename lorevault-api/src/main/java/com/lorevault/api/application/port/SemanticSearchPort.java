package com.lorevault.api.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Port for semantic search operations using vector embeddings.
 * Provides vector similarity search capabilities across chunk content.
 */
public interface SemanticSearchPort {

    /**
     * Perform semantic search using query embedding vector.
     * 
     * @param queryEmbedding Vector representation of search query
     * @param topK Maximum number of results to return
     * @param filters Optional filters to restrict search scope
     * @return List of search results ordered by relevance score (descending)
     */
    List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters);

    /**
     * Check if semantic search is available (i.e., chunks with embeddings exist).
     * 
     * @return true if search can be performed, false otherwise
     */
    boolean isAvailable();

    /**
     * Search result containing chunk information and similarity score.
     */
    record SearchResult(
        UUID chunkId,
        double score,
        String snippet,
        UUID chapterId,
        Integer bookNumber,
        Integer chapterNumber
    ) {}

    /**
     * Optional filters for constraining search scope.
     */
    record SearchFilters(
        String universe,
        String series,
        Integer bookNumber,
        Integer chapterNumber
    ) {
        public static SearchFilters empty() {
            return new SearchFilters(null, null, null, null);
        }

        public boolean hasFilters() {
            return universe != null || series != null || bookNumber != null || chapterNumber != null;
        }
    }
}
