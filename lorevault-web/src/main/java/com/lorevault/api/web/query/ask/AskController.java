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
@Tag(name = "Query", description = "Content search and Q&A operations")
@Slf4j
@RequiredArgsConstructor
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
            log.error("Semantic search failed for query '{}': {}", request.getQuery(), e.getMessage());
            log.debug("Semantic search failure details for query '{}'", request.getQuery(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Baseline RAG QA: vector retrieval across corpus + generated answer.
     */
    @PostMapping("/ask/rag")
    public ResponseEntity<AskResponse> askRag(@Valid @RequestBody AskRequest request) {
        log.info("RAG question answering request: question='{}', topK={}", 
                request.getQuestion(), request.getTopK());

        try {
            AskResponse response = ragService.askRagBaseline(request);
            log.info("RAG completed: answer length={} chars, citations={} in {}ms", 
                    response.getAnswer().length(), 
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("RAG failed for question '{}': {}", request.getQuestion(), e.getMessage());
            log.debug("RAG failure details for question '{}'", request.getQuestion(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Graph-aware QA endpoint for routed entity lookup + narrative fallback.
     */
    @PostMapping("/ask/graph-aware")
    public ResponseEntity<AskResponse> askGraphAware(@Valid @RequestBody AskRequest request) {
        log.info("Graph-aware QA request: question='{}', topK={}",
                request.getQuestion(), request.getTopK());

        try {
            AskResponse response = ragService.askGraphAware(request);
            log.info("Graph-aware QA completed: answer length={} chars, citations={} in {}ms",
                    response.getAnswer().length(),
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Graph-aware QA failed for question '{}': {}", request.getQuestion(), e.getMessage());
            log.debug("Graph-aware QA failure details for question '{}'", request.getQuestion(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Hybrid QA endpoint for side-by-side retrieval strategy comparison.
     * Runs parallel vector + graph retrieval with deduplicated RRF fusion.
     */
    @PostMapping("/ask/hybrid")
    public ResponseEntity<AskResponse> askHybrid(@Valid @RequestBody AskRequest request) {
        log.info("Hybrid QA request: question='{}', topK={}",
                request.getQuestion(), request.getTopK());

        try {
            AskResponse response = ragService.askHybrid(request);
            log.info("Hybrid QA completed: answer length={} chars, citations={} in {}ms",
                    response.getAnswer().length(),
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Hybrid QA failed for question '{}': {}", request.getQuestion(), e.getMessage());
            log.debug("Hybrid QA failure details for question '{}'", request.getQuestion(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
