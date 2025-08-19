package com.lorevault.api.service.search;

import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.application.port.SemanticSearchPort;
import com.lorevault.api.application.port.SemanticSearchPort.SearchFilters;
import com.lorevault.api.application.port.SemanticSearchPort.SearchResult;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchFilters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemanticSearchServiceTest {

    @Mock private EmbeddingPort embeddingPort;
    @Mock private SemanticSearchPort semanticSearchPort;
    
    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(embeddingPort, semanticSearchPort);
    }

    @Test
    void search_WithValidQuery_ReturnsResults() {
        // Given
        String query = "character development arc";
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery(query);
        request.setTopK(5);

        double[] queryEmbedding = {0.1, 0.2, 0.3};
        List<SearchResult> searchResults = List.of(
            new SearchResult(UUID.randomUUID(), 0.95, "Character grows stronger", UUID.randomUUID(), 1, 3),
            new SearchResult(UUID.randomUUID(), 0.88, "Development through trials", UUID.randomUUID(), 1, 5)
        );

        when(embeddingPort.embed(query)).thenReturn(queryEmbedding);
        when(semanticSearchPort.search(eq(queryEmbedding), eq(5), any(SearchFilters.class)))
            .thenReturn(searchResults);

        // When
        SemanticSearchResponse response = service.search(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getScore()).isEqualTo(0.95);
        assertThat(response.getResults().get(0).getSnippet()).isEqualTo("Character grows stronger");
        
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getQuery()).isEqualTo(query);
        assertThat(response.getMetadata().getTotalResults()).isEqualTo(2);
        assertThat(response.getMetadata().getReturnedResults()).isEqualTo(2);

        verify(embeddingPort).embed(query);
        verify(semanticSearchPort).search(eq(queryEmbedding), eq(5), any(SearchFilters.class));
    }

    @Test
    void search_WithFilters_PassesFiltersToPort() {
        // Given
        String query = "magic system";
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery(query);
        request.setTopK(3);
        
        SemanticSearchFilters filters = new SemanticSearchFilters();
        filters.setUniverse("Cosmere");
        filters.setBookNumber(1);
        request.setFilters(filters);

        double[] queryEmbedding = {0.4, 0.5, 0.6};
        when(embeddingPort.embed(query)).thenReturn(queryEmbedding);
        when(semanticSearchPort.search(any(double[].class), eq(3), any(SearchFilters.class)))
            .thenReturn(List.of());

        // When
        service.search(request);

        // Then
        verify(semanticSearchPort).search(
            eq(queryEmbedding), 
            eq(3), 
            argThat(searchFilters -> 
                "Cosmere".equals(searchFilters.universe()) && 
                Integer.valueOf(1).equals(searchFilters.bookNumber())
            )
        );
    }

    @Test
    void search_WithEmptyResults_ReturnsEmptyResponse() {
        // Given
        String query = "nonexistent content";
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery(query);

        double[] queryEmbedding = {0.1, 0.2};
        when(embeddingPort.embed(query)).thenReturn(queryEmbedding);
        when(semanticSearchPort.search(any(double[].class), anyInt(), any(SearchFilters.class)))
            .thenReturn(List.of());

        // When
        SemanticSearchResponse response = service.search(request);

        // Then
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getMetadata().getTotalResults()).isZero();
        assertThat(response.getMetadata().getReturnedResults()).isZero();
    }

    @Test
    void search_WithNullFilters_CreatesEmptyFilters() {
        // Given
        String query = "test query";
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery(query);
        request.setFilters(null);

        double[] queryEmbedding = {0.1};
        when(embeddingPort.embed(query)).thenReturn(queryEmbedding);
        when(semanticSearchPort.search(any(double[].class), anyInt(), any(SearchFilters.class)))
            .thenReturn(List.of());

        // When
        service.search(request);

        // Then
        verify(semanticSearchPort).search(
            eq(queryEmbedding), 
            eq(5), 
            argThat(searchFilters -> !searchFilters.hasFilters())
        );
    }

    @Test
    void isAvailable_DelegatesToPort() {
        // Given
        when(semanticSearchPort.isAvailable()).thenReturn(true);

        // When
        boolean available = service.isAvailable();

        // Then
        assertThat(available).isTrue();
        verify(semanticSearchPort).isAvailable();
    }

    @Test
    void search_WithEmbeddingFailure_PropagatesException() {
        // Given
        String query = "test query";
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery(query);

        when(embeddingPort.embed(query)).thenThrow(new RuntimeException("Embedding service unavailable"));

        // When/Then
        assertThatThrownBy(() -> service.search(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Embedding service unavailable");
    }
}
