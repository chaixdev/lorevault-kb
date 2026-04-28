package com.lorevault.api.web.ui;

import com.lorevault.api.web.query.ask.AskDtos;
import com.lorevault.api.web.query.ask.AskDtos.CitationDto;
import com.lorevault.api.web.query.ask.SemanticSearchDtos;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskResponse;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchResponse;
import com.lorevault.api.search.rag.RagService;
import com.lorevault.api.search.semantic.SemanticSearchService;
import com.lorevault.api.search.model.EntityLookupException;
import com.lorevault.api.search.model.SemanticSearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/ui/query")
@RequiredArgsConstructor
@Slf4j
public class UiQueryController {

    private final RagService ragService;
    private final SemanticSearchService semanticSearchService;

    @PostMapping("/ask/vector")
    public String askVector(@RequestParam("question") String question,
                            @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                            Model model) {
        CoreSemanticSearchRequest coreRequest = new CoreSemanticSearchRequest(
            question, topK, null, null, null
        );

        try {
            CoreSemanticSearchResponse coreResponse = semanticSearchService.search(coreRequest);
        
            List<SearchResultDto> results = coreResponse.results().stream()
                .map(r -> SearchResultDto.of(
                    r.chunkId(), r.score(), r.snippet(), r.chapterId(),
                    r.bookNumber(), r.chapterNumber(), r.sceneId(),
                    r.sceneSummary(), r.individualsPresent(), r.locationsPresent()
                ))
                .toList();
             
            SemanticSearchDtos.SearchMetadata metadata = SemanticSearchDtos.SearchMetadata.of(
                coreResponse.metadata().query(),
                coreResponse.metadata().totalResults(),
                coreResponse.metadata().returnedResults(),
                coreResponse.metadata().processingTimeMs()
            );
         
            SemanticSearchDtos.SemanticSearchResponse searchResponse = SemanticSearchDtos.SemanticSearchResponse.of(results, metadata);
         
            model.addAttribute("question", question);
            model.addAttribute("mode", "Vector retrieval");
            model.addAttribute("response", searchResponse);
            return "ui/query :: vectorResponse";
        } catch (SemanticSearchException e) {
            return serviceUnavailable(model, question, "Vector retrieval", e);
        }
    }

    @PostMapping("/ask/rag")
    public String askRag(@RequestParam("question") String question,
                         @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                         Model model) {
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, null
        );

        try {
            CoreAskResponse coreResponse = ragService.askRagBaseline(coreRequest);
            AskDtos.AskResponse askResponse = mapToAskResponse(coreResponse);
         
            model.addAttribute("question", question);
            model.addAttribute("mode", "RAG baseline");
            model.addAttribute("response", askResponse);
            return "ui/query :: ragResponse";
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable(model, question, "RAG baseline", e);
        }
    }

    @PostMapping("/ask/graph-aware")
    public String askGraphAware(@RequestParam("question") String question,
                                @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                                Model model) {
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, null
        );

        try {
            CoreAskResponse coreResponse = ragService.askGraphAware(coreRequest);
            AskDtos.AskResponse askResponse = mapToAskResponse(coreResponse);
         
            model.addAttribute("question", question);
            model.addAttribute("mode", "Graph-aware");
            model.addAttribute("response", askResponse);
            return "ui/query :: ragResponse";
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable(model, question, "Graph-aware", e);
        }
    }

    @PostMapping("/ask/hybrid")
    public String askHybrid(@RequestParam("question") String question,
                            @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                            Model model) {
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, null
        );

        try {
            CoreAskResponse coreResponse = ragService.askHybrid(coreRequest);
            AskDtos.AskResponse askResponse = mapToAskResponse(coreResponse);
         
            model.addAttribute("question", question);
            model.addAttribute("mode", "Hybrid RRF");
            model.addAttribute("response", askResponse);
            return "ui/query :: ragResponse";
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable(model, question, "Hybrid RRF", e);
        }
    }

    private String serviceUnavailable(Model model,
                                      String question,
                                      String mode,
                                      RuntimeException exception) {
        log.warn("UI query unavailable for mode '{}' and question '{}': {}", mode, question, exception.getMessage());
        log.debug("UI query business failure details for mode '{}' and question '{}'", mode, question, exception);
        model.addAttribute("question", question);
        model.addAttribute("mode", mode);
        model.addAttribute("errorMessage", "Search is temporarily unavailable. Please try again.");
        return "ui/query :: queryError";
    }

    private AskDtos.AskResponse mapToAskResponse(CoreAskResponse coreResponse) {
        List<CitationDto> citations = coreResponse.citations().stream()
            .map(c -> CitationDto.of(
                c.chunkId(),
                c.score(),
                c.snippet(),
                c.coordinates()
            ))
            .toList();
            
        AskDtos.AskMetadata metadata = AskDtos.AskMetadata.of(
            coreResponse.metadata().question(),
            coreResponse.metadata().chunksRetrieved(),
            coreResponse.metadata().chunksUsed(),
            coreResponse.metadata().processingTimeMs(),
            coreResponse.metadata().modelId()
        );
        
        return AskDtos.AskResponse.of(coreResponse.answer(), citations, metadata);
    }
}
