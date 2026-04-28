package com.lorevault.api.web.query.ask;

import com.lorevault.api.web.query.ask.AskDtos.AskRequest;
import com.lorevault.api.web.query.ask.AskDtos.AskResponse;
import com.lorevault.api.web.query.ask.AskDtos.CitationDto;
import com.lorevault.api.web.query.ask.AskDtos.AskMetadata;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SearchMetadata;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskResponse;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskFilters;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchResponse;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchFilters;
import com.lorevault.api.search.rag.RagService;
import com.lorevault.api.search.semantic.SemanticSearchService;
import com.lorevault.api.search.model.EntityLookupException;
import com.lorevault.api.search.model.SemanticSearchException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            CoreSemanticSearchRequest coreRequest = mapToCoreSemanticSearchRequest(request);
            CoreSemanticSearchResponse coreResponse = semanticSearchService.search(coreRequest);
            SemanticSearchResponse response = mapToSemanticSearchResponse(coreResponse);
            
            log.info("Semantic search completed: returned {} results in {}ms", 
                    response.getResults().size(), response.getMetadata().getProcessingTimeMs());
            
            return ResponseEntity.ok(response);
        } catch (SemanticSearchException e) {
            return serviceUnavailable("Semantic search", request.getQuery(), e);
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
            CoreAskRequest coreRequest = mapToCoreAskRequest(request);
            CoreAskResponse coreResponse = ragService.askRagBaseline(coreRequest);
            AskResponse response = mapToAskResponse(coreResponse);
            
            log.info("RAG completed: answer length={} chars, citations={} in {}ms", 
                    response.getAnswer().length(), 
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());
            
            return ResponseEntity.ok(response);
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable("RAG", request.getQuestion(), e);
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
            CoreAskRequest coreRequest = mapToCoreAskRequest(request);
            CoreAskResponse coreResponse = ragService.askGraphAware(coreRequest);
            AskResponse response = mapToAskResponse(coreResponse);
            
            log.info("Graph-aware QA completed: answer length={} chars, citations={} in {}ms",
                    response.getAnswer().length(),
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());

            return ResponseEntity.ok(response);
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable("Graph-aware QA", request.getQuestion(), e);
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
            CoreAskRequest coreRequest = mapToCoreAskRequest(request);
            CoreAskResponse coreResponse = ragService.askHybrid(coreRequest);
            AskResponse response = mapToAskResponse(coreResponse);
            
            log.info("Hybrid QA completed: answer length={} chars, citations={} in {}ms",
                    response.getAnswer().length(),
                    response.getCitations().size(),
                    response.getMetadata().getProcessingTimeMs());

            return ResponseEntity.ok(response);
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable("Hybrid QA", request.getQuestion(), e);
        } catch (Exception e) {
            log.error("Hybrid QA failed for question '{}': {}", request.getQuestion(), e.getMessage());
            log.debug("Hybrid QA failure details for question '{}'", request.getQuestion(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private <T> ResponseEntity<T> serviceUnavailable(String operation, String query, RuntimeException e) {
        log.warn("{} unavailable for query '{}': {}", operation, query, e.getMessage());
        log.debug("{} business failure details for query '{}'", operation, query, e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    // --- Mappers ---

    private CoreSemanticSearchRequest mapToCoreSemanticSearchRequest(SemanticSearchRequest request) {
        CoreSemanticSearchFilters filters = null;
        if (request.getFilters() != null) {
            filters = new CoreSemanticSearchFilters(
                request.getFilters().getUniverse(),
                request.getFilters().getSeries(),
                request.getFilters().getBookNumber(),
                request.getFilters().getChapterNumber()
            );
        }
        return new CoreSemanticSearchRequest(
            request.getQuery(),
            request.getTopK(),
            request.getThreshold(),
            filters,
            request.getVisibility()
        );
    }

    private SemanticSearchResponse mapToSemanticSearchResponse(CoreSemanticSearchResponse coreResponse) {
        List<SearchResultDto> results = coreResponse.results().stream()
            .map(r -> SearchResultDto.of(
                r.chunkId(),
                r.score(),
                r.snippet(),
                r.chapterId(),
                r.bookNumber(),
                r.chapterNumber(),
                r.sceneId(),
                r.sceneSummary(),
                r.individualsPresent(),
                r.locationsPresent()
            ))
            .toList();
            
        SearchMetadata metadata = SearchMetadata.of(
            coreResponse.metadata().query(),
            coreResponse.metadata().totalResults(),
            coreResponse.metadata().returnedResults(),
            coreResponse.metadata().processingTimeMs()
        );
        
        return SemanticSearchResponse.of(results, metadata);
    }

    private CoreAskRequest mapToCoreAskRequest(AskRequest request) {
        CoreAskFilters filters = null;
        if (request.getFilters() != null) {
            filters = new CoreAskFilters(
                request.getFilters().getUniverse(),
                request.getFilters().getSeries(),
                request.getFilters().getBookNumber(),
                request.getFilters().getChapterNumber()
            );
        }
        return new CoreAskRequest(
            request.getQuestion(),
            request.getTopK(),
            request.getThreshold(),
            filters,
            request.getVisibility()
        );
    }

    private AskResponse mapToAskResponse(CoreAskResponse coreResponse) {
        List<CitationDto> citations = coreResponse.citations().stream()
            .map(c -> CitationDto.of(
                c.chunkId(),
                c.score(),
                c.snippet(),
                c.coordinates()
            ))
            .toList();
            
        AskMetadata metadata = AskMetadata.of(
            coreResponse.metadata().question(),
            coreResponse.metadata().chunksRetrieved(),
            coreResponse.metadata().chunksUsed(),
            coreResponse.metadata().processingTimeMs(),
            coreResponse.metadata().modelId()
        );
        
        return AskResponse.of(coreResponse.answer(), citations, metadata);
    }
}
