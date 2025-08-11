package com.lorevault.api.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Port for text embedding and semantic search capabilities.
 * Abstracts away the specific AI/ML service used for embeddings.
 */
public interface EmbeddingPort {
    
    /**
     * Generate an embedding vector for the given text.
     * 
     * @param text The text to generate embeddings for
     * @return A list of float values representing the text embedding
     * @throws EmbeddingException if the embedding generation fails
     */
    List<Float> generateEmbedding(String text);
    
    /**
     * Generate embeddings for all chunks belonging to a chapter.
     * 
     * @param chapterId The UUID of the chapter
     * @return The number of chunks that had embeddings generated
     */
    int generateEmbeddingsForChapter(UUID chapterId);
    
    /**
     * Search for semantically similar content using vector similarity.
     * 
     * @param queryEmbedding The embedding vector to search with
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return List of search results with similarity scores
     */
    List<SemanticSearchResult> searchSimilar(List<Float> queryEmbedding, int limit, double threshold);
    
    /**
     * Search for semantically similar content using text query.
     * Convenience method that generates embedding from text and searches.
     * 
     * @param queryText The text query to search for
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return List of search results with similarity scores
     */
    List<SemanticSearchResult> searchSimilar(String queryText, int limit, double threshold);
    
    /**
     * Get information about the current embedding model being used.
     * 
     * @return Model information including name, dimensions, etc.
     */
    EmbeddingModelInfo getModelInfo();
    
    /**
     * Data class for search results.
     */
    record SemanticSearchResult(
            UUID chunkId,
            String content,
            double similarityScore,
            UUID chapterId,
            String chapterTitle
    ) {}
    
    /**
     * Data class for embedding model information.
     */
    record EmbeddingModelInfo(
            String modelName,
            int dimensions,
            String provider
    ) {}
}
