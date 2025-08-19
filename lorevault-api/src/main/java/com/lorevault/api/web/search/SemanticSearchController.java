package com.lorevault.api.web.search;

import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.service.search.SemanticSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for semantic search endpoints.
 * Provides vector-based similarity search across chunk content.
 */
@RestController
@RequestMapping("/api/query/search")
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    /**
     * Perform semantic search using natural language query.
     * 
     * @param request Search request containing query and optional filters
     * @return Search results with similarity scores
     */
    @PostMapping("/semantic")
    public ResponseEntity<SemanticSearchResponse> search(@Valid @RequestBody SemanticSearchRequest request) {
        log.info("Semantic search request: query='{}', topK={}", request.getQuery(), request.getTopK());

        // Check if semantic search is available
        if (!semanticSearchService.isAvailable()) {
            log.warn("Semantic search requested but no embeddings available");
            return ResponseEntity.status(503)
                .header("Retry-After", "300") // Suggest retry in 5 minutes
                .build();
        }

        try {
            SemanticSearchResponse response = semanticSearchService.search(request);
            log.info("Semantic search completed: returned {} results in {}ms", 
                    response.getResults().size(), response.getMetadata().getProcessingTimeMs());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Semantic search failed for query '{}': {}", request.getQuery(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check availability of semantic search functionality.
     * 
     * @return Status indicating whether semantic search is available
     */
    @GetMapping("/semantic/status")
    public ResponseEntity<SearchStatusResponse> getSearchStatus() {
        boolean available = semanticSearchService.isAvailable();
        SearchStatusResponse status = new SearchStatusResponse(available);
        
        return ResponseEntity.ok(status);
    }

    /**
     * Status response for search availability.
     */
    public record SearchStatusResponse(
        boolean available,
        String message
    ) {
        public SearchStatusResponse(boolean available) {
            this(available, available ? 
                "Semantic search is available" : 
                "Semantic search is not available - no embeddings found");
        }
    }
}
