package com.lorevault.api.web.ui;

import com.lorevault.api.search.AskDtos;
import com.lorevault.api.search.RagService;
import com.lorevault.api.search.SemanticSearchDtos;
import com.lorevault.api.search.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        SemanticSearchDtos.SemanticSearchRequest request = new SemanticSearchDtos.SemanticSearchRequest();
        request.setQuery(question);
        request.setTopK(topK);

        SemanticSearchDtos.SemanticSearchResponse response = semanticSearchService.search(request);
        model.addAttribute("question", question);
        model.addAttribute("mode", "Vector retrieval");
        model.addAttribute("response", response);
        return "ui/query :: vectorResponse";
    }

    @PostMapping("/ask/rag")
    public String askRag(@RequestParam("question") String question,
                         @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                         Model model) {
        AskDtos.AskRequest request = new AskDtos.AskRequest();
        request.setQuestion(question);
        request.setTopK(topK);

        AskDtos.AskResponse response = ragService.askRagBaseline(request);
        model.addAttribute("question", question);
        model.addAttribute("mode", "RAG baseline");
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
    }

    @PostMapping("/ask/graph-aware")
    public String askGraphAware(@RequestParam("question") String question,
                                @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                                Model model) {
        AskDtos.AskRequest request = new AskDtos.AskRequest();
        request.setQuestion(question);
        request.setTopK(topK);

        AskDtos.AskResponse response = ragService.askGraphAware(request);
        model.addAttribute("question", question);
        model.addAttribute("mode", "Graph-aware");
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
    }

    @PostMapping("/ask/hybrid")
    public String askHybrid(@RequestParam("question") String question,
                            @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                            Model model) {
        AskDtos.AskRequest request = new AskDtos.AskRequest();
        request.setQuestion(question);
        request.setTopK(topK);

        AskDtos.AskResponse response = ragService.askHybrid(request);
        model.addAttribute("question", question);
        model.addAttribute("mode", "Hybrid RRF");
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
    }
}
