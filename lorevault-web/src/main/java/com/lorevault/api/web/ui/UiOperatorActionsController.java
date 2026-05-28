package com.lorevault.api.web.ui;

import com.lorevault.api.ingestion.resolution.location.BookLocationConsolidationService;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualConsolidationService;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationConsolidationService;
import com.lorevault.api.ingestion.resolution.location.BookLocationConsolidationResult;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualConsolidationResult;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationConsolidationResult;
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

    private final ChapterIndividualConsolidationService chapterIndividualConsolidationService;
    private final ChapterLocationConsolidationService chapterLocationConsolidationService;
    private final BookLocationConsolidationService bookLocationConsolidationService;

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-individuals")
    public String consolidateChapterIndividuals(@PathVariable UUID chapterId, Model model) {
        ChapterIndividualConsolidationResult response = chapterIndividualConsolidationService.consolidateChapter(chapterId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-locations")
    public String consolidateChapterLocations(@PathVariable UUID chapterId, Model model) {
        ChapterLocationConsolidationResult response = chapterLocationConsolidationService.consolidateChapter(chapterId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/books/{bookId}/chapter-consolidate-locations")
    public String consolidateBookLocations(@PathVariable UUID bookId, Model model) {
        BookLocationConsolidationResult response = bookLocationConsolidationService.consolidateBook(bookId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }
}
