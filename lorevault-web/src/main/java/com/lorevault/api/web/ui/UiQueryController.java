package com.lorevault.api.web.ui;

import com.lorevault.api.web.query.ask.AskDtos;
import com.lorevault.api.web.query.ask.AskDtos.CitationDto;
import com.lorevault.api.web.query.ask.SemanticSearchDtos;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.search.application.CoreSearchRecords.CoreAskRequest;
import com.lorevault.api.search.application.CoreSearchRecords.CoreAskResponse;
import com.lorevault.api.search.application.CoreSearchRecords.CoreSemanticSearchRequest;
import com.lorevault.api.search.application.CoreSearchRecords.CoreSemanticSearchResponse;
import com.lorevault.api.search.application.RagService;
import com.lorevault.api.search.application.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/ui/query")
@RequiredArgsConstructor
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
        
        SemanticSearchDtos.SemanticSearchResponse response = SemanticSearchDtos.SemanticSearchResponse.of(results, metadata);
        
        model.addAttribute("question", question);
        model.addAttribute("mode", "Vector retrieval");
        model.addAttribute("response", response);
        return "ui/query :: vectorResponse";
    }

    @PostMapping("/ask/rag")
    public String askRag(@RequestParam("question") String question,
                         @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                         Model model) {
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, null
        );

        CoreAskResponse coreResponse = ragService.askRagBaseline(coreRequest);
        AskDtos.AskResponse response = mapToAskResponse(coreResponse);
        
        model.addAttribute("question", question);
        model.addAttribute("mode", "RAG baseline");
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
    }

    @PostMapping("/ask/graph-aware")
    public String askGraphAware(@RequestParam("question") String question,
                                @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                                Model model) {
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, null
        );

        CoreAskResponse coreResponse = ragService.askGraphAware(coreRequest);
        AskDtos.AskResponse response = mapToAskResponse(coreResponse);
        
        model.addAttribute("question", question);
        model.addAttribute("mode", "Graph-aware");
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
    }

    @PostMapping("/ask/hybrid")
    public String askHybrid(@RequestParam("question") String question,
                            @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                            Model model) {
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, null
        );

        CoreAskResponse coreResponse = ragService.askHybrid(coreRequest);
        AskDtos.AskResponse response = mapToAskResponse(coreResponse);
        
        model.addAttribute("question", question);
        model.addAttribute("mode", "Hybrid RRF");
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
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
