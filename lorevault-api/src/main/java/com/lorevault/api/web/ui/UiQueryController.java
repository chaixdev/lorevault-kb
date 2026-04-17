package com.lorevault.api.web.ui;

import com.lorevault.api.search.AskDtos;
import com.lorevault.api.search.RagService;
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

    @PostMapping("/ask/rag")
    public String askRag(@RequestParam("question") String question,
                         @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                         Model model) {
        AskDtos.AskRequest request = new AskDtos.AskRequest();
        request.setQuestion(question);
        request.setTopK(topK);

        AskDtos.AskResponse response = ragService.ask(request);
        model.addAttribute("question", question);
        model.addAttribute("response", response);
        return "ui/query :: ragResponse";
    }
}
