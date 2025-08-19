package com.lorevault.api.web.qa;

import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.service.search.SemanticSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Query controller for Ask endpoints following CQRS conventions.
 * Provides vector-only and RAG variants for comparison.
 */
@RestController
@RequestMapping("/api/query/ask")
@RequiredArgsConstructor
@Slf4j
public class AskController {

    private final SemanticSearchService semanticSearchService;
    // TODO: Inject RagService when implemented

    /**
     * Vector-only QA: returns top chunks with scores. This mirrors existing semantic search
     * but namespaced under /api/query/ask/vector for evolution comparison.
     */
    @PostMapping("/vector")
    public ResponseEntity<SemanticSearchResponse> askVector(@Valid @RequestBody SemanticSearchRequest request) {
        log.info("Ask (vector) request: query='{}' topK={}", request.getQuery(), request.getTopK());

        if (!semanticSearchService.isAvailable()) {
            return ResponseEntity.status(503).header("Retry-After", "300").build();
        }
        try {
            return ResponseEntity.ok(semanticSearchService.search(request));
        } catch (Exception e) {
            log.error("Ask (vector) failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * RAG QA: will retrieve chunks and synthesize an answer with citations.
     * Placeholder endpoint to establish API surface; will be implemented in v0.8.0.
     */
    @PostMapping("/rag")
    public ResponseEntity<?> askRag(@RequestBody String body) {
        // TODO: define AskDtos and RagService, then implement
        return ResponseEntity.status(501).body("RAG endpoint not implemented yet");
    }
}
