package com.lorevault.api.web.ui;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.IngestionPipelineCoordinator;
import com.lorevault.api.graph.location.consolidation.book.BookLocationConsolidationService;
import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationService;
import com.lorevault.api.graph.location.consolidation.chapter.ChapterLocationConsolidationService;
import com.lorevault.api.graph.location.consolidation.book.BookLocationConsolidationResult;
import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationResult;
import com.lorevault.api.graph.location.consolidation.chapter.ChapterLocationConsolidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/ui/actions")
@RequiredArgsConstructor
@Slf4j
public class UiOperatorActionsController {

    private final ChapterIndividualConsolidationService chapterIndividualConsolidationService;
    private final ChapterLocationConsolidationService chapterLocationConsolidationService;
    private final BookLocationConsolidationService bookLocationConsolidationService;
    private final IngestionPipelineCoordinator pipelineCoordinator;

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-individuals")
    public String consolidateChapterIndividuals(@PathVariable UUID chapterId, Model model) {
        var ctx = new StageExecutionContext(UUID.randomUUID(), UUID.randomUUID(), chapterId, null, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
        ChapterIndividualConsolidationResult response = chapterIndividualConsolidationService.consolidateChapter(ctx, chapterId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-locations")
    public String consolidateChapterLocations(@PathVariable UUID chapterId, Model model) {
        var ctx = new StageExecutionContext(UUID.randomUUID(), UUID.randomUUID(), chapterId, null, StageKey.CHAPTER_LOCATION_CONSOLIDATION);
        ChapterLocationConsolidationResult response = chapterLocationConsolidationService.consolidateChapter(ctx, chapterId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/books/{bookId}/chapter-consolidate-locations")
    public String consolidateBookLocations(@PathVariable UUID bookId, Model model) {
        var ctx = new StageExecutionContext(UUID.randomUUID(), UUID.randomUUID(), null, bookId, StageKey.BOOK_LOCATION_CONSOLIDATION);
        BookLocationConsolidationResult response = bookLocationConsolidationService.consolidateBook(ctx, bookId);
        model.addAttribute("message", response.message());
        model.addAttribute("tone", response.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/chapters/{chapterId}/replay")
    public String replayChapter(@PathVariable UUID chapterId, Model model) {
        try {
            UUID jobId = pipelineCoordinator.findJobIdByChapterId(chapterId);
            UUID bookId = pipelineCoordinator.findBookIdByChapterId(chapterId);
            if (jobId == null) {
                model.addAttribute("message", "No ingestion job found for this chapter.");
                model.addAttribute("tone", "error");
                return "ui/jobs :: actionToast";
            }
            pipelineCoordinator.rerunStage(jobId, chapterId, bookId, StageKey.SCENE_SEGMENTATION);
            log.info("[REPLAY] Chapter replay initiated: chapterId={}, jobId={}", chapterId, jobId);
            model.addAttribute("message", "Replay initiated — destroying effects and reingesting chapter.");
            model.addAttribute("tone", "success");
        } catch (Exception e) {
            log.error("[REPLAY] Failed: chapterId={}: {}", chapterId, e.getMessage(), e);
            model.addAttribute("message", "Replay failed: " + e.getMessage());
            model.addAttribute("tone", "error");
        }
        return "ui/jobs :: actionToast";
    }
}
