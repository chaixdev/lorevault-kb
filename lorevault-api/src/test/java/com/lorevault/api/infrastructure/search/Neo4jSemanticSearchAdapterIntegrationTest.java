package com.lorevault.api.infrastructure.search;

import com.lorevault.api.application.port.SemanticSearchPort;
import com.lorevault.api.application.port.SemanticSearchPort.SearchFilters;
import com.lorevault.api.application.port.SemanticSearchPort.SearchResult;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChunkGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Neo4jSemanticSearchAdapter.
 * Tests vector index creation, search ranking, and edge cases.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class Neo4jSemanticSearchAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.20")
            .withAdminPassword("testpassword");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
        // Enable Neo4j semantic search adapter
        registry.add("lorevault.search.provider", () -> "neo4j");
    }

    @Autowired
    private SemanticSearchPort semanticSearchPort;

    @Autowired
    private ChunkGraphRepository chunkRepository;

    @BeforeEach
    void setUp() {
        // Clear database
        chunkRepository.deleteAll();
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
        List<SearchResult> results = semanticSearchPort.search(queryEmbedding, 3, SearchFilters.empty());
        
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
        List<SearchResult> results = semanticSearchPort.search(queryEmbedding, 2, SearchFilters.empty());
        
        // Then: Only 2 results returned
        assertThat(results).hasSize(2);
    }

    @Test
    void search_withNoEmbeddedChunks_returnsEmptyResults() {
        // Given: No chunks in database
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        // When: Performing search
        List<SearchResult> results = semanticSearchPort.search(queryEmbedding, 5, SearchFilters.empty());
        
        // Then: Empty results
        assertThat(results).isEmpty();
    }

    @Test
    void search_withChunksWithoutEmbeddings_returnsEmptyResults() {
        // Given: Chunk without embedding
        ChunkNode chunk = new ChunkNode();
        chunk.setId(UUID.randomUUID());
        chunk.setText("Text without embedding");
        chunk.setContentHash("hash123");
        chunkRepository.save(chunk);
        
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        // When: Performing search
        List<SearchResult> results = semanticSearchPort.search(queryEmbedding, 5, SearchFilters.empty());
        
        // Then: No results (chunks without embeddings ignored)
        assertThat(results).isEmpty();
    }

    @Test
    void isAvailable_withEmbeddedChunks_returnsTrue() {
        // Given: At least one chunk with embedding
        createChunkWithEmbedding(UUID.randomUUID(), "Test chunk", new double[]{1.0, 0.0, 0.0});
        
        // When/Then: Service is available
        assertThat(semanticSearchPort.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_withNoEmbeddedChunks_returnsFalse() {
        // Given: No chunks with embeddings
        
        // When/Then: Service is not available
        assertThat(semanticSearchPort.isAvailable()).isFalse();
    }

    @Test
    void search_withNegativeSimilarity_filtersOutResults() {
        // Given: Query and chunk that result in negative cosine similarity
        UUID chunkId = UUID.randomUUID();
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        double[] chunkEmbedding = {-1.0, 0.0, 0.0}; // Opposite direction = negative similarity
        
        createChunkWithEmbedding(chunkId, "Opposite chunk", chunkEmbedding);
        
        // When: Performing search
        List<SearchResult> results = semanticSearchPort.search(queryEmbedding, 5, SearchFilters.empty());
        
        // Then: Results filtered out due to negative similarity
        assertThat(results).isEmpty();
    }

    private void createChunkWithEmbedding(UUID chunkId, String text, double[] embedding) {
        ChunkNode chunk = new ChunkNode();
        chunk.setId(chunkId);
        chunk.setText(text);
        chunk.setContentHash("hash_" + text.hashCode());
        chunk.setEmbedding(embedding);
        chunk.setEmbeddingHash("test_hash_" + chunkId);
        chunk.setEmbeddedAt(LocalDateTime.now());
        chunkRepository.save(chunk);
    }
}