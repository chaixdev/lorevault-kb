package com.lorevault.api.search;

import com.lorevault.api.search.Neo4jSemanticSearchAdapter;
import com.lorevault.api.search.Neo4jSemanticSearchAdapter.SearchFilters;
import com.lorevault.api.search.Neo4jSemanticSearchAdapter.SearchResult;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.search.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.search.SemanticSearchDtos.SearchMetadata;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchFilters;
import com.lorevault.api.support.SpoilerVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for performing semantic search across chunk content.
 * Orchestrates query embedding generation and vector similarity search.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final EmbeddingModel embeddingModel;
    private final Neo4jSemanticSearchAdapter semanticSearchPort;

    /**
     * Perform semantic search for the given query.
     * 
     * @param request Search request with query and optional filters
     * @return Search results with metadata
     */
    public SemanticSearchResponse search(SemanticSearchRequest request) {
        log.debug("Performing semantic search for query: '{}' with topK: {}", 
                 request.getQuery(), request.getTopK());
        
        long startTime = System.currentTimeMillis();

        // Generate query embedding
        float[] queryEmbeddingRaw = embeddingModel.embed(request.getQuery());
        double[] queryEmbedding = toDoubleArray(queryEmbeddingRaw);
        log.debug("Generated embedding vector of dimension: {}", queryEmbedding.length);

        // Convert filters
        SearchFilters searchFilters = convertFilters(request.getFilters());

        // Perform search
        List<SearchResult> searchResults = semanticSearchPort.search(
            queryEmbedding,
            request.getTopK(),
            searchFilters,
            request.getVisibility()
        );

        // Convert results
        List<SearchResultDto> resultDtos = searchResults.stream()
            .map(this::convertSearchResult)
            .toList();

        long processingTime = System.currentTimeMillis() - startTime;
        
        SearchMetadata metadata = SearchMetadata.of(
            request.getQuery(),
            searchResults.size(),
            resultDtos.size(),
            processingTime
        );

        log.debug("Semantic search completed in {}ms, found {} results", 
                 processingTime, searchResults.size());

        return SemanticSearchResponse.of(resultDtos, metadata);
    }

    /**
     * Check if semantic search is currently available.
     * 
     * @return true if search can be performed, false otherwise
     */
    public boolean isAvailable() {
        return semanticSearchPort.isAvailable();
    }

    private SearchFilters convertFilters(SemanticSearchFilters filters) {
        if (filters == null) {
            return SearchFilters.empty();
        }
        
        return new SearchFilters(
            filters.getUniverse(),
            filters.getSeries(), 
            filters.getBookNumber(),
            filters.getChapterNumber()
        );
    }

    private SearchResultDto convertSearchResult(SearchResult result) {
        return SearchResultDto.of(
            result.chunkId(),
            result.score(),
            result.snippet(),
            result.chapterId(),
            result.bookNumber(),
            result.chapterNumber()
        );
    }

    private double[] toDoubleArray(float[] vector) {
        if (vector == null) {
            return new double[0];
        }
        double[] out = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = vector[i];
        }
        return out;
    }
}
