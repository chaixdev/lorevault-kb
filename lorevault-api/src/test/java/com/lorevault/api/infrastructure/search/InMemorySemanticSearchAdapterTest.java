package com.lorevault.api.infrastructure.search;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.SemanticSearchPort.SearchFilters;
import com.lorevault.api.application.port.SemanticSearchPort.SearchResult;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InMemorySemanticSearchAdapterTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    
    private InMemorySemanticSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemorySemanticSearchAdapter(contentPersistencePort);
    }

    @Test
    void search_WithEmbeddedChunks_ReturnsRankedResults() {
        // Given
        double[] queryEmbedding = {1.0, 0.0, 0.0}; // Perfect match with first chunk
        
        ChunkNode chunk1 = createChunkWithEmbedding("First chunk text", new double[]{1.0, 0.0, 0.0}); // Exact match
        ChunkNode chunk2 = createChunkWithEmbedding("Second chunk text", new double[]{0.0, 1.0, 0.0}); // No similarity
        ChunkNode chunk3 = createChunkWithEmbedding("Third chunk text", new double[]{0.8, 0.6, 0.0}); // Some similarity

        when(contentPersistencePort.findAllChunksWithEmbeddings())
            .thenReturn(List.of(chunk1, chunk2, chunk3));

        // When
        List<SearchResult> results = adapter.search(queryEmbedding, 5, SearchFilters.empty());

        // Then
        assertThat(results).hasSize(2); // chunk2 has 0.0 similarity, so filtered out
        
        // Results should be ordered by similarity (descending)
        assertThat(results.get(0).chunkId()).isEqualTo(chunk1.getId());
        assertThat(results.get(0).score()).isCloseTo(1.0, within(0.001)); // Perfect match
        
        assertThat(results.get(1).chunkId()).isEqualTo(chunk3.getId());
        assertThat(results.get(1).score()).isGreaterThan(0.0);
        assertThat(results.get(1).score()).isLessThan(1.0);
    }

    @Test
    void search_WithTopKLimit_ReturnsLimitedResults() {
        // Given
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        ChunkNode chunk1 = createChunkWithEmbedding("Text 1", new double[]{1.0, 0.0, 0.0});
        ChunkNode chunk2 = createChunkWithEmbedding("Text 2", new double[]{0.9, 0.1, 0.0});
        ChunkNode chunk3 = createChunkWithEmbedding("Text 3", new double[]{0.8, 0.2, 0.0});
        ChunkNode chunk4 = createChunkWithEmbedding("Text 4", new double[]{0.7, 0.3, 0.0});

        when(contentPersistencePort.findAllChunksWithEmbeddings())
            .thenReturn(List.of(chunk1, chunk2, chunk3, chunk4));

        // When
        List<SearchResult> results = adapter.search(queryEmbedding, 2, SearchFilters.empty());

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
    }

    @Test
    void search_WithNoEmbeddedChunks_ReturnsEmptyList() {
        // Given
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        when(contentPersistencePort.findAllChunksWithEmbeddings()).thenReturn(List.of());

        // When
        List<SearchResult> results = adapter.search(queryEmbedding, 5, SearchFilters.empty());

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void search_WithChunksWithoutEmbeddings_FiltersThemOut() {
        // Given
        double[] queryEmbedding = {1.0, 0.0, 0.0};
        
        ChunkNode validChunk = createChunkWithEmbedding("Valid text", new double[]{1.0, 0.0, 0.0});
        ChunkNode invalidChunk = createChunkNode("Invalid text", null);

        when(contentPersistencePort.findAllChunksWithEmbeddings())
            .thenReturn(List.of(validChunk, invalidChunk));

        // When
        List<SearchResult> results = adapter.search(queryEmbedding, 5, SearchFilters.empty());

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(validChunk.getId());
    }

    @Test
    void search_CreatesSnippets() {
        // Given
        String longText = "This is a very long text that should be truncated to create a snippet for display purposes. " +
                         "It contains many words and should be cut off at a reasonable point to avoid overwhelming the user " +
                         "with too much information in the search results.";
        
        ChunkNode chunk = createChunkWithEmbedding(longText, new double[]{1.0, 0.0, 0.0});
        
        when(contentPersistencePort.findAllChunksWithEmbeddings()).thenReturn(List.of(chunk));

        // When
        List<SearchResult> results = adapter.search(new double[]{1.0, 0.0, 0.0}, 5, SearchFilters.empty());

        // Then
        assertThat(results).hasSize(1);
        String snippet = results.get(0).snippet();
        assertThat(snippet).isNotNull();
        assertThat(snippet.length()).isLessThanOrEqualTo(203); // 200 + "..."
        assertThat(snippet).endsWith("...");
    }

    @Test
    void isAvailable_WithEmbeddedChunks_ReturnsTrue() {
        // Given
        when(contentPersistencePort.findAllChunksWithEmbeddings())
            .thenReturn(List.of(createChunkWithEmbedding("Text", new double[]{1.0, 0.0})));

        // When
        boolean available = adapter.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    void isAvailable_WithNoEmbeddedChunks_ReturnsFalse() {
        // Given
        when(contentPersistencePort.findAllChunksWithEmbeddings()).thenReturn(List.of());

        // When
        boolean available = adapter.isAvailable();

        // Then
        assertThat(available).isFalse();
    }

    @Test
    void search_HandlesException_ReturnsEmptyList() {
        // Given
        when(contentPersistencePort.findAllChunksWithEmbeddings())
            .thenThrow(new RuntimeException("Database error"));

        // When
        List<SearchResult> results = adapter.search(new double[]{1.0, 0.0}, 5, SearchFilters.empty());

        // Then
        assertThat(results).isEmpty();
    }

    private ChunkNode createChunkWithEmbedding(String text, double[] embedding) {
        ChunkNode chunk = createChunkNode(text, embedding);
        chunk.setEmbeddingHash("hash-" + chunk.getId().toString());
        return chunk;
    }

    private ChunkNode createChunkNode(String text, double[] embedding) {
        ChunkNode chunk = new ChunkNode();
        chunk.setId(UUID.randomUUID());
        chunk.setText(text);
        chunk.setEmbedding(embedding);
        return chunk;
    }
}
