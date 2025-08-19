package com.lorevault.api.service.search;

import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.application.port.SemanticSearchPort;
import com.lorevault.api.application.port.SemanticSearchPort.SearchFilters;
import com.lorevault.api.application.port.SemanticSearchPort.SearchResult;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchMetadata;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final EmbeddingPort embeddingPort;
    private final SemanticSearchPort semanticSearchPort;

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
        double[] queryEmbedding = embeddingPort.embed(request.getQuery());
        log.debug("Generated embedding vector of dimension: {}", queryEmbedding.length);

        // Convert filters
        SearchFilters searchFilters = convertFilters(request.getFilters());

        // Perform search
        List<SearchResult> searchResults = semanticSearchPort.search(
            queryEmbedding, 
            request.getTopK(), 
            searchFilters
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
}
