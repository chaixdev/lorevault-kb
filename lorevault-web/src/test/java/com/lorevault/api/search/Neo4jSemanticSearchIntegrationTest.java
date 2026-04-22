package com.lorevault.api.search;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.content.Chunk;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.search.infrastructure.Neo4jSemanticSearch.SearchFilters;
import com.lorevault.api.search.infrastructure.Neo4jSemanticSearch.SearchResult;
import com.lorevault.api.search.domain.SeriesProgress;
import com.lorevault.api.search.domain.SpoilerVisibility;
import com.lorevault.api.search.domain.UnconfiguredSeriesPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Neo4jSemanticSearch.
 * Tests vector index creation, search ranking, and edge cases.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class Neo4jSemanticSearchIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpassword");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
        // Enable Neo4j-backed semantic search
        registry.add("lorevault.search.provider", () -> "neo4j");
    }

    @Autowired
    private Neo4jSemanticSearch semanticSearch;

    @Autowired
    private ChunkGraphRepository chunkRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setUp() {
        // Clear database
        chunkRepository.deleteAll();
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void search_withEmbeddedChunks_returnsRankedResults() {
        // Given: Chunks with known embeddings for predictable ranking
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();
        UUID chunkId3 = UUID.randomUUID();
        
        // Query embedding: [1.0, 0.0, 0.0] (will be closest to chunk1)
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        // Create chunks with varying similarity to query
        createChunkWithEmbedding(chunkId1, "First chunk text", new double[]{1.0, 0.0, 0.0}); // Perfect match
        createChunkWithEmbedding(chunkId2, "Second chunk text", new double[]{0.5, 0.5, 0.0}); // Partial match  
        createChunkWithEmbedding(chunkId3, "Third chunk text", new double[]{0.0, 1.0, 0.0}); // Orthogonal
        
        // When: Performing semantic search
        List<SearchResult> results = semanticSearch.search(queryEmbedding, 3, SearchFilters.empty());
        
        // Then: Results ranked by similarity score (descending)
        assertThat(results).hasSize(3);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkId1);
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(1).chunkId()).isEqualTo(chunkId2);
        assertThat(results.get(2).chunkId()).isEqualTo(chunkId3);
        
        // Verify snippets are included
        assertThat(results.get(0).snippet()).contains("First chunk text");
    }

    @Test
    void search_withTopKLimit_returnsOnlyTopResults() {
        // Given: Multiple chunks
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        createChunkWithEmbedding(UUID.randomUUID(), "Chunk 1", new double[]{1.0, 0.0, 0.0});
        createChunkWithEmbedding(UUID.randomUUID(), "Chunk 2", new double[]{0.8, 0.2, 0.0});
        createChunkWithEmbedding(UUID.randomUUID(), "Chunk 3", new double[]{0.6, 0.4, 0.0});
        createChunkWithEmbedding(UUID.randomUUID(), "Chunk 4", new double[]{0.4, 0.6, 0.0});
        
        // When: Requesting only top 2
        List<SearchResult> results = semanticSearch.search(queryEmbedding, 2, SearchFilters.empty());
        
        // Then: Only 2 results returned
        assertThat(results).hasSize(2);
    }

    @Test
    void search_withNoEmbeddedChunks_returnsEmptyResults() {
        // Given: No chunks in database
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        // When: Performing search
        List<SearchResult> results = semanticSearch.search(queryEmbedding, 5, SearchFilters.empty());
        
        // Then: Empty results
        assertThat(results).isEmpty();
    }

    @Test
    void search_withChunksWithoutEmbeddings_returnsEmptyResults() {
        // Given: Chunk without embedding
        Chunk chunk = new Chunk();
        chunk.setId(UUID.randomUUID());
        chunk.setText("Text without embedding");
        chunk.setContentHash("hash123");
        chunkRepository.save(chunk);
        
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        // When: Performing search
        List<SearchResult> results = semanticSearch.search(queryEmbedding, 5, SearchFilters.empty());
        
        // Then: No results (chunks without embeddings ignored)
        assertThat(results).isEmpty();
    }

    @Test
    void isAvailable_withEmbeddedChunks_returnsTrue() {
        // Given: At least one chunk with embedding
        createChunkWithEmbedding(UUID.randomUUID(), "Test chunk", new double[]{1.0, 0.0, 0.0});
        
        // When/Then: Service is available
        assertThat(semanticSearch.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_withNoEmbeddedChunks_returnsFalse() {
        // Given: No chunks with embeddings
        
        // When/Then: Service is not available
        assertThat(semanticSearch.isAvailable()).isFalse();
    }

    @Test
    void search_withNegativeSimilarity_filtersOutResults() {
        // Given: Query and chunk that result in negative cosine similarity
        UUID chunkId = UUID.randomUUID();
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        double[] chunkEmbedding = {-1.0, 0.0, 0.0}; // Opposite direction = negative similarity
        
        createChunkWithEmbedding(chunkId, "Opposite chunk", chunkEmbedding);
        
        // When: Performing search
        List<SearchResult> results = semanticSearch.search(queryEmbedding, 5, SearchFilters.empty());
        
        // Then: Results filtered out due to negative similarity
        assertThat(results).isEmpty();
    }

    // ─── Filter tests ──────────────────────────────────────────────────────────

    @Test
    void search_withUniverseFilter_returnsOnlyMatchingUniverse() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunkA = UUID.randomUUID();
        UUID chunkB = UUID.randomUUID();

        createChunkLinkedToChapter(chunkA, "Cosmere chunk", new double[]{1.0, 0.0, 0.0},
                "Cosmere", null, null, null);
        createChunkLinkedToChapter(chunkB, "Other universe chunk", new double[]{0.9, 0.1, 0.0},
                "OtherUniverse", null, null, null);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, new SearchFilters("Cosmere", null, null, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkA);
    }

    @Test
    void search_withBookNumberFilter_returnsOnlyMatchingBook() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunkBook1 = UUID.randomUUID();
        UUID chunkBook2 = UUID.randomUUID();

        createChunkLinkedToChapter(chunkBook1, "Book 1 chunk", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 1, null);
        createChunkLinkedToChapter(chunkBook2, "Book 2 chunk", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Stormlight", 2, null);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, new SearchFilters("Cosmere", "Stormlight", 1, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkBook1);
    }

    @Test
    void search_withChapterNumberFilter_returnsOnlyMatchingChapter() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunkCh1 = UUID.randomUUID();
        UUID chunkCh2 = UUID.randomUUID();

        createChunkLinkedToChapter(chunkCh1, "Chapter 1 chunk", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 1, 1);
        createChunkLinkedToChapter(chunkCh2, "Chapter 2 chunk", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Stormlight", 1, 2);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, new SearchFilters("Cosmere", "Stormlight", 1, 1));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkCh1);
    }

    @Test
    void search_withEmptyFilters_returnsAllMatchingChunks() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunkA = UUID.randomUUID();
        UUID chunkB = UUID.randomUUID();

        createChunkLinkedToChapter(chunkA, "Chunk A", new double[]{1.0, 0.0, 0.0},
                "UniverseA", "SeriesA", 1, 1);
        createChunkLinkedToChapter(chunkB, "Chunk B", new double[]{0.8, 0.2, 0.0},
                "UniverseB", "SeriesB", 2, 3);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty());

        assertThat(results).hasSize(2);
    }

    @Test
    void search_withSpoilerVisibility_hidesChunksBeyondReadingProgress() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunkBook1 = UUID.randomUUID();
        UUID chunkBook2 = UUID.randomUUID();
        UUID chunkBook3 = UUID.randomUUID();

        createChunkLinkedToChapter(chunkBook1, "Safe chunk book 1", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 1, 10);
        createChunkLinkedToChapter(chunkBook2, "Safe chunk book 2", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Stormlight", 2, 1);
        createChunkLinkedToChapter(chunkBook3, "Spoiler chunk book 3", new double[]{0.8, 0.2, 0.0},
                "Cosmere", "Stormlight", 3, 1);

        SeriesProgress progress = new SeriesProgress();
        progress.setSeries("Stormlight");
        progress.setReadThroughBookNumber(2);
        progress.setReadThroughChapterNumber(null);

        SpoilerVisibility visibility = new SpoilerVisibility();
        visibility.setUniverse("Cosmere");
        visibility.setSeriesProgress(List.of(progress));

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty(), visibility);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SearchResult::chunkId)
                .containsExactlyInAnyOrder(chunkBook1, chunkBook2);
    }

    @Test
    void search_withSpoilerVisibility_respectsChapterBoundary() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunkBeforeCutoff = UUID.randomUUID();
        UUID chunkAtCutoff     = UUID.randomUUID();
        UUID chunkAfterCutoff  = UUID.randomUUID();

        createChunkLinkedToChapter(chunkBeforeCutoff, "Ch 4 content", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Mistborn", 1, 4);
        createChunkLinkedToChapter(chunkAtCutoff, "Ch 5 content", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Mistborn", 1, 5);
        createChunkLinkedToChapter(chunkAfterCutoff, "Ch 6 content", new double[]{0.8, 0.2, 0.0},
                "Cosmere", "Mistborn", 1, 6);

        SeriesProgress progress = new SeriesProgress();
        progress.setSeries("Mistborn");
        progress.setReadThroughBookNumber(1);
        progress.setReadThroughChapterNumber(5);

        SpoilerVisibility visibility = new SpoilerVisibility();
        visibility.setUniverse("Cosmere");
        visibility.setSeriesProgress(List.of(progress));

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty(), visibility);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SearchResult::chunkId)
                .containsExactlyInAnyOrder(chunkBeforeCutoff, chunkAtCutoff);
    }

    @Test
    void search_withMultipleSeriesProgress_filtersEachSeriesIndependently() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID stormlight1 = UUID.randomUUID();
        UUID stormlight5 = UUID.randomUUID();
        UUID mistborn1   = UUID.randomUUID();
        UUID mistborn4   = UUID.randomUUID();

        createChunkLinkedToChapter(stormlight1, "SA book 1", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 1, 1);
        createChunkLinkedToChapter(stormlight5, "SA book 5", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Stormlight", 5, 1);
        createChunkLinkedToChapter(mistborn1, "MB book 1", new double[]{0.85, 0.15, 0.0},
                "Cosmere", "Mistborn", 1, 1);
        createChunkLinkedToChapter(mistborn4, "MB book 4", new double[]{0.8, 0.2, 0.0},
                "Cosmere", "Mistborn", 4, 1);

        SeriesProgress saProgress = new SeriesProgress();
        saProgress.setSeries("Stormlight");
        saProgress.setReadThroughBookNumber(2);

        SeriesProgress mbProgress = new SeriesProgress();
        mbProgress.setSeries("Mistborn");
        mbProgress.setReadThroughBookNumber(3);

        SpoilerVisibility visibility = new SpoilerVisibility();
        visibility.setUniverse("Cosmere");
        visibility.setSeriesProgress(List.of(saProgress, mbProgress));

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty(), visibility);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SearchResult::chunkId)
                .containsExactlyInAnyOrder(stormlight1, mistborn1);
    }

    @Test
    void search_withUnconfiguredSeriesPolicyHide_excludesUnregisteredSeries() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID registeredChunk   = UUID.randomUUID();
        UUID unregisteredChunk = UUID.randomUUID();

        createChunkLinkedToChapter(registeredChunk, "Known series chunk", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 1, 1);
        createChunkLinkedToChapter(unregisteredChunk, "Unknown series chunk", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Elantris", 1, 1);

        SeriesProgress progress = new SeriesProgress();
        progress.setSeries("Stormlight");
        progress.setReadThroughBookNumber(5);

        SpoilerVisibility visibility = new SpoilerVisibility();
        visibility.setUniverse("Cosmere");
        visibility.setSeriesProgress(List.of(progress));
        visibility.setUnconfiguredSeriesPolicy(UnconfiguredSeriesPolicy.HIDE);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty(), visibility);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(registeredChunk);
    }

    @Test
    void search_withUnconfiguredSeriesPolicyShow_includesUnregisteredSeries() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID registeredChunk   = UUID.randomUUID();
        UUID unregisteredChunk = UUID.randomUUID();

        createChunkLinkedToChapter(registeredChunk, "Known series chunk", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 1, 1);
        createChunkLinkedToChapter(unregisteredChunk, "Unknown series chunk", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Elantris", 1, 1);

        SeriesProgress progress = new SeriesProgress();
        progress.setSeries("Stormlight");
        progress.setReadThroughBookNumber(5);

        SpoilerVisibility visibility = new SpoilerVisibility();
        visibility.setUniverse("Cosmere");
        visibility.setSeriesProgress(List.of(progress));
        visibility.setUnconfiguredSeriesPolicy(UnconfiguredSeriesPolicy.SHOW);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty(), visibility);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SearchResult::chunkId)
                .containsExactlyInAnyOrder(registeredChunk, unregisteredChunk);
    }

    @Test
    void search_withNullVisibility_returnsAllChunksUnfiltered() {
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        createChunkLinkedToChapter(chunk1, "Series A book 99", new double[]{1.0, 0.0, 0.0},
                "Cosmere", "Stormlight", 99, 99);
        createChunkLinkedToChapter(chunk2, "Series B book 99", new double[]{0.9, 0.1, 0.0},
                "Cosmere", "Mistborn", 99, 99);

        List<SearchResult> results = semanticSearch.search(
                queryEmbedding, 5, SearchFilters.empty(), null);

        assertThat(results).hasSize(2);
    }

    /**
     * Creates a Chunk with an embedding and links it via a Chapter node using raw Cypher.
     * This bypasses the SDN entity graph to keep the test setup simple and fast.
     */
    private void createChunkLinkedToChapter(UUID chunkId, String text, double[] embedding,
                                            String universe, String series,
                                            Integer bookNumber, Integer chapterNumber) {
        UUID chapterId = UUID.randomUUID();
        java.util.List<Double> embeddingList = java.util.Arrays.stream(embedding)
                .boxed()
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("chunkId",       chunkId.toString());
        params.put("text",          text);
        params.put("hash",          "hash_" + chunkId);
        params.put("embedding",     embeddingList);
        params.put("embeddingHash", "emb_" + chunkId);
        params.put("chapterId",     chapterId.toString());
        params.put("universe",      universe);
        params.put("series",        series);
        params.put("bookNumber",    bookNumber);
        params.put("chapterNumber", chapterNumber);

        neo4jClient.query("""
            CREATE (chapter:Chapter {
                id: $chapterId,
                universe: $universe,
                series: $series,
                bookNumber: $bookNumber,
                chapterNumber: $chapterNumber
            })
            CREATE (chunk:Chunk {
                id: $chunkId,
                text: $text,
                contentHash: $hash,
                embedding: $embedding,
                embeddingHash: $embeddingHash
            })
            CREATE (chapter)-[:HAS_CHUNK]->(chunk)
            """)
                .bindAll(params)
                .run();
    }

    private void createChunkWithEmbedding(UUID chunkId, String text, double[] embedding) {
        Chunk chunk = new Chunk();
        chunk.setId(chunkId);
        chunk.setText(text);
        chunk.setContentHash("hash_" + text.hashCode());
        chunk.setEmbedding(embedding);
        chunk.setEmbeddingHash("test_hash_" + chunkId);
        chunk.setEmbeddedAt(LocalDateTime.now());
        chunkRepository.save(chunk);
    }
}
