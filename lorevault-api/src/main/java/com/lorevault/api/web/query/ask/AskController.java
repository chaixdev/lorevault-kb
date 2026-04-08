package com.lorevault.api.web.query.ask;

import com.lorevault.api.search.AskDtos.AskRequest;
import com.lorevault.api.search.AskDtos.AskResponse;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.search.RagService;
import com.lorevault.api.search.SemanticSearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Query controller for Ask and Search endpoints following CQRS conventions.
 * Provides vector search, vector QA, and RAG QA variants for comparison.
 */
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Query", description = "Content search and Q&A operations")
public class AskController {

    private final SemanticSearchService semanticSearchService;
    private final RagService ragService;

    /**
     * Vector-only QA: returns top chunks with scores for evolution comparison.
     */
    @PostMapping("/ask/vector")
    public ResponseEntity<SemanticSearchResponse> askVector(@Valid @RequestBody SemanticSearchRequest request) {
        log.info("Semantic search request: query='{}', topK={}", request.getQuery(), request.getTopK());

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
     * RAG QA: will retrieve chunks and synthesize an answer with citations.
     * Placeholder endpoint to establish API surface; will be implemented in v0.8.0.
     */
    @PostMapping("/ask/rag")
    public ResponseEntity<AskResponse> askRag(@Valid @RequestBody AskRequest request) {
        log.info("RAG question answering request: question='{}', topK={}", 
                request.getQuestion(), request.getTopK());

        try {
            AskResponse response = ragService.ask(request);
            log.info("RAG completed: answer length={} chars, citations={} in {}ms", 
                    response.getAnswer().length(), 
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("RAG failed for question '{}': {}", request.getQuestion(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
