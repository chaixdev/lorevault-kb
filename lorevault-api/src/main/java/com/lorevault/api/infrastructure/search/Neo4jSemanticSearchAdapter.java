package com.lorevault.api.infrastructure.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Neo4j-native semantic search implementation using vector indices.
 * Uses Neo4j's db.index.vector.queryNodes for efficient similarity search.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "lorevault.search.provider", havingValue = "neo4j", matchIfMissing = true)
public class Neo4jSemanticSearchAdapter {

    public record SearchResult(
            UUID chunkId,
            double score,
            String snippet,
            UUID chapterId,
            Integer bookNumber,
            Integer chapterNumber
    ) {}

    public record SearchFilters(
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

    private final Neo4jClient neo4jClient;
    @org.springframework.beans.factory.annotation.Value("${lorevault.search.snippet.max-length:600}")
    private int maxSnippetLength;
    
    // Configuration - could be externalized to properties later
    private static final String VECTOR_INDEX_NAME = "chunk_embedding_idx";
    
    public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters) {
        log.debug("Performing Neo4j vector search with topK: {} and filters: {}", topK, filters);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Use oversample strategy to handle post-filtering
            // Request more results than needed, then filter and limit
            int oversampleLimit = topK * 3; // Conservative oversampling
            
            // Convert double[] to List<Double> for Neo4j parameter binding
            java.util.List<Double> embeddingList = java.util.Arrays.stream(queryEmbedding)
                    .boxed()
                    .collect(java.util.stream.Collectors.toList());

            // Normalise filter values: treat empty string as null so callers
            // cannot accidentally bypass a filter with an empty string.
            String universe    = blankToNull(filters != null ? filters.universe()    : null);
            String series      = blankToNull(filters != null ? filters.series()      : null);
            Integer bookNumber  = filters != null ? filters.bookNumber()    : null;
            Integer chapterNumber = filters != null ? filters.chapterNumber() : null;

            // Query Neo4j vector index for semantic similarity.
            // Filter predicates use ($param IS NULL OR chapter.prop = $param) so the
            // same static query works whether a filter value is set or not.
            List<SearchResult> results = neo4jClient.query("""
                CALL db.index.vector.queryNodes($indexName, $limit, $embedding)
                YIELD node, score
                WITH node AS chunk, score
                // Try both relationship patterns: direct Chapter->HAS_CHUNK->Chunk (legacy)
                // and Chapter->HAS_SCENE->Scene->HAS_CHUNK->Chunk (current)
                OPTIONAL MATCH (chapterDirect:Chapter)-[:HAS_CHUNK]->(chunk)
                OPTIONAL MATCH (chapterViaScene:Chapter)-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(chunk)
                WITH chunk, score, coalesce(chapterViaScene, chapterDirect) AS chapter
                WHERE score > 0.0
                  AND ($universe      IS NULL OR chapter.universe      = $universe)
                  AND ($series        IS NULL OR chapter.series        = $series)
                  AND ($bookNumber    IS NULL OR chapter.bookNumber    = $bookNumber)
                  AND ($chapterNumber IS NULL OR chapter.chapterNumber = $chapterNumber)
                RETURN
                    chunk.id AS chunkId,
                    score,
                    chunk.text AS text,
                    chapter.id AS chapterId,
                    chapter.bookNumber AS bookNumber,
                    chapter.chapterNumber AS chapterNumber
                ORDER BY score DESC
                LIMIT $topK
                """)
                .bindAll(buildParams(VECTOR_INDEX_NAME, oversampleLimit, embeddingList, topK,
                                    universe, series, bookNumber, chapterNumber))
                .fetchAs(SearchResult.class)
                .mappedBy((typeSystem, record) -> {
                    UUID chunkId = UUID.fromString(record.get("chunkId").asString());
                    double score = record.get("score").asDouble();
                    String text = record.get("text").asString();
                    String snippet = truncateSnippet(text);

                    UUID chapterId = record.get("chapterId").isNull() ? null : UUID.fromString(record.get("chapterId").asString());
                    Integer mappedBookNumber = record.get("bookNumber").isNull() ? null : record.get("bookNumber").asInt();
                    Integer mappedChapterNumber = record.get("chapterNumber").isNull() ? null : record.get("chapterNumber").asInt();

                    return new SearchResult(chunkId, score, snippet, chapterId, mappedBookNumber, mappedChapterNumber);
                })
                .all()
                .stream()
                .collect(java.util.stream.Collectors.toList());
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("Neo4j vector search completed in {}ms, returning {} results", 
                     processingTime, results.size());
            
            return results;
            
        } catch (Exception e) {
            log.warn("Neo4j vector search failed: {}. Ensure vector index '{}' exists.", 
                     e.getMessage(), VECTOR_INDEX_NAME);
            return List.of(); // Graceful fallback to empty results
        }
    }
    
    public boolean isAvailable() {
        try {
            // Check if vector index exists and has data
            Long count = neo4jClient.query("""
                CALL db.indexes() YIELD name, type, state
                WHERE name = $indexName AND type = 'VECTOR' AND state = 'ONLINE'
                RETURN count(*) as indexCount
                """)
                .bind("indexName").to(VECTOR_INDEX_NAME)
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
                
            if (count == 0) {
                log.debug("Vector index '{}' not available", VECTOR_INDEX_NAME);
                return false;
            }
            
            // Check if there are chunks with embeddings
            Long chunkCount = neo4jClient.query("""
                MATCH (ch:Chunk) 
                WHERE ch.embedding IS NOT NULL AND ch.embeddingHash IS NOT NULL
                RETURN count(ch) as chunkCount
                LIMIT 1
                """)
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
            
            boolean available = chunkCount > 0;
            log.debug("Neo4j semantic search available: {} (index exists: {}, chunks with embeddings: {})", 
                     available, count > 0, chunkCount);
            
            return available;
            
        } catch (Exception e) {
            log.warn("Failed to check Neo4j vector search availability: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Build the parameter map for the vector search query.
     * Uses a HashMap (not Map.of) to allow null values for optional filter parameters.
     * Null filter values are passed as null so the Cypher "$param IS NULL" predicate evaluates correctly.
     */
    private Map<String, Object> buildParams(
            String indexName, int limit, java.util.List<Double> embedding, int topK,
            String universe, String series, Integer bookNumber, Integer chapterNumber) {
        Map<String, Object> params = new HashMap<>();
        params.put("indexName",     indexName);
        params.put("limit",         limit);
        params.put("embedding",     embedding);
        params.put("topK",          topK);
        params.put("universe",      universe);
        params.put("series",        series);
        params.put("bookNumber",    bookNumber);
        params.put("chapterNumber", chapterNumber);
        return params;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
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
