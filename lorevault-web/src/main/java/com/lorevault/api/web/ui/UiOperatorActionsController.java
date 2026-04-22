package com.lorevault.api.web.ui;

import com.lorevault.api.ingestion.application.resolution.BookLocationReductionService;
import com.lorevault.api.ingestion.application.resolution.ChapterIndividualResolutionService;
import com.lorevault.api.ingestion.application.resolution.ChapterLocationResolutionService;
import com.lorevault.api.ingestion.application.result.BookLocationResolutionResult;
import com.lorevault.api.ingestion.application.result.ChapterIndividualResolutionResult;
import com.lorevault.api.ingestion.application.result.ChapterLocationResolutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/ui/actions")
@RequiredArgsConstructor
public class UiOperatorActionsController {

    private final ChapterIndividualResolutionService chapterIndividualResolutionService;
    private final ChapterLocationResolutionService chapterLocationResolutionService;
    private final BookLocationReductionService bookLocationReductionService;

    @PostMapping("/chapters/{chapterId}/resolve-individuals")
    public String resolveChapterIndividuals(@PathVariable UUID chapterId, Model model) {
        ChapterIndividualResolutionResult response = chapterIndividualResolutionService.resolveChapter(chapterId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/chapters/{chapterId}/resolve-locations")
    public String resolveChapterLocations(@PathVariable UUID chapterId, Model model) {
        ChapterLocationResolutionResult response = chapterLocationResolutionService.resolveChapter(chapterId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/books/{bookId}/resolve-locations")
    public String resolveBookLocations(@PathVariable UUID bookId, Model model) {
        BookLocationResolutionResult response = bookLocationReductionService.resolveBook(bookId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }
}
