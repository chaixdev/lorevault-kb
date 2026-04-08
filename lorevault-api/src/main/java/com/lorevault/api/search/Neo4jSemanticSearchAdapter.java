package com.lorevault.api.search;

import com.lorevault.api.support.SeriesProgress;
import com.lorevault.api.support.SpoilerVisibility;
import com.lorevault.api.support.UnconfiguredSeriesPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static final String VECTOR_INDEX_NAME = "chunk_embedding_idx";

    private final Neo4jClient neo4jClient;

    @Value("${lorevault.search.snippet.max-length:600}")
    private int maxSnippetLength;

    @Value("${lorevault.search.oversample-multiplier:3}")
    private int oversampleMultiplier;

    public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters) {
        return search(queryEmbedding, topK, filters, null);
    }

    public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters,
                                     SpoilerVisibility visibility) {
        log.debug("Performing Neo4j vector search with topK: {}, filters: {}, visibility: {}",
                topK, filters, visibility != null ? "present" : "absent");

        long startTime = System.currentTimeMillis();

        try {
            int oversampleLimit = topK * oversampleMultiplier;

            java.util.List<Double> embeddingList = java.util.Arrays.stream(queryEmbedding)
                    .boxed()
                    .collect(java.util.stream.Collectors.toList());

            String universe    = blankToNull(filters != null ? filters.universe()    : null);
            String series      = blankToNull(filters != null ? filters.series()      : null);
            Integer bookNumber  = filters != null ? filters.bookNumber()    : null;
            Integer chapterNumber = filters != null ? filters.chapterNumber() : null;

            String cypher = buildCypher(visibility);
            Map<String, Object> params = buildParams(
                    VECTOR_INDEX_NAME, oversampleLimit, embeddingList, topK,
                    universe, series, bookNumber, chapterNumber, visibility);

            List<SearchResult> results = neo4jClient.query(cypher)
                    .bindAll(params)
                    .fetchAs(SearchResult.class)
                    .mappedBy((typeSystem, record) -> {
                        UUID chunkId = UUID.fromString(record.get("chunkId").asString());
                        double score = record.get("score").asDouble();
                        String text = record.get("text").asString();
                        String snippet = truncateSnippet(text);

                        UUID chapterId = record.get("chapterId").isNull()
                                ? null : UUID.fromString(record.get("chapterId").asString());
                        Integer mappedBookNumber = record.get("bookNumber").isNull()
                                ? null : record.get("bookNumber").asInt();
                        Integer mappedChapterNumber = record.get("chapterNumber").isNull()
                                ? null : record.get("chapterNumber").asInt();

                        return new SearchResult(chunkId, score, snippet, chapterId,
                                mappedBookNumber, mappedChapterNumber);
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
            return List.of();
        }
    }

    public boolean isAvailable() {
        try {
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

    private String buildCypher(SpoilerVisibility visibility) {
        String spoilerClause = visibility == null
                ? ""
                : buildSpoilerClause(visibility);

        return """
            CALL db.index.vector.queryNodes($indexName, $limit, $embedding)
            YIELD node, score
            WITH node AS chunk, score
            OPTIONAL MATCH (chapterDirect:Chapter)-[:HAS_CHUNK]->(chunk)
            OPTIONAL MATCH (chapterViaScene:Chapter)-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(chunk)
            WITH chunk, score, coalesce(chapterViaScene, chapterDirect) AS chapter
            WHERE score > 0.0
              AND ($universe      IS NULL OR chapter.universe      = $universe)
              AND ($series        IS NULL OR chapter.series        = $series)
              AND ($bookNumber    IS NULL OR chapter.bookNumber    = $bookNumber)
              AND ($chapterNumber IS NULL OR chapter.chapterNumber = $chapterNumber)
            """ + spoilerClause + """
            RETURN
                chunk.id AS chunkId,
                score,
                chunk.text AS text,
                chapter.id AS chapterId,
                chapter.bookNumber AS bookNumber,
                chapter.chapterNumber AS chapterNumber
            ORDER BY score DESC
            LIMIT $topK
            """;
    }

    private String buildSpoilerClause(SpoilerVisibility visibility) {
        boolean showUnconfigured = visibility.getUnconfiguredSeriesPolicy() == UnconfiguredSeriesPolicy.SHOW;

        String progressPredicate = """
              AND (
                ANY(p IN $visibilityProgress WHERE
                  chapter.universe = $visibilityUniverse
                  AND chapter.series = p.series
                  AND (
                    chapter.bookNumber < p.readThroughBookNumber
                    OR (chapter.bookNumber = p.readThroughBookNumber
                        AND coalesce(p.readThroughChapterNumber, 999999) >= chapter.chapterNumber)
                  )
                )
            """;

        if (showUnconfigured) {
            progressPredicate += """
                  OR NOT ANY(p IN $visibilityProgress WHERE
                    chapter.universe = $visibilityUniverse
                    AND chapter.series = p.series
                  )
                )
                """;
        } else {
            progressPredicate += "  )\n";
        }

        return progressPredicate;
    }

    private Map<String, Object> buildParams(
            String indexName, int limit, java.util.List<Double> embedding, int topK,
            String universe, String series, Integer bookNumber, Integer chapterNumber,
            SpoilerVisibility visibility) {

        Map<String, Object> params = new HashMap<>();
        params.put("indexName",     indexName);
        params.put("limit",         limit);
        params.put("embedding",     embedding);
        params.put("topK",          topK);
        params.put("universe",      universe);
        params.put("series",        series);
        params.put("bookNumber",    bookNumber);
        params.put("chapterNumber", chapterNumber);

        if (visibility != null) {
            params.put("visibilityUniverse", visibility.getUniverse());
            params.put("visibilityProgress", serializeProgress(visibility.getSeriesProgress()));
        }

        return params;
    }

    private List<Map<String, Object>> serializeProgress(List<SeriesProgress> progress) {
        if (progress == null) return List.of();
        return progress.stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("series", p.getSeries());
                    m.put("readThroughBookNumber", p.getReadThroughBookNumber());
                    m.put("readThroughChapterNumber", p.getReadThroughChapterNumber());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String truncateSnippet(String text) {
        if (text == null) return null;
        int maxLength = Math.max(50, maxSnippetLength);
        if (text.length() <= maxLength) return text;
        int breakPoint = text.lastIndexOf(' ', maxLength - 3);
        if (breakPoint < maxLength / 2) breakPoint = maxLength - 3;
        return text.substring(0, breakPoint) + "...";
    }
}
