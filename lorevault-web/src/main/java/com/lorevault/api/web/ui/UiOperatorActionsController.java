package com.lorevault.api.web.ui;

import com.lorevault.api.common.ExceptionSanitizer;
import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationHandler;
import com.lorevault.api.graph.location.consolidation.chapter.ChapterLocationConsolidationHandler;
import com.lorevault.api.graph.location.consolidation.book.BookLocationConsolidationHandler;
import com.lorevault.api.orchestration.pipeline.IngestionPipelineCoordinator;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;
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

    private final ChapterIndividualConsolidationHandler chapterIndividualConsolidator;
    private final ChapterLocationConsolidationHandler chapterLocationConsolidator;
    private final BookLocationConsolidationHandler bookLocationConsolidator;
    private final IngestionPipelineCoordinator pipelineCoordinator;

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-individuals")
    public String consolidateChapterIndividuals(@PathVariable UUID chapterId, Model model) {
        var ctx = new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
        StageResult result = chapterIndividualConsolidator.execute(ctx);
        model.addAttribute("message", result.success() ? result.summary() : "Consolidation skipped or failed");
        model.addAttribute("tone", result.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/chapters/{chapterId}/chapter-consolidate-locations")
    public String consolidateChapterLocations(@PathVariable UUID chapterId, Model model) {
        var ctx = new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_LOCATION_CONSOLIDATION);
        StageResult result = chapterLocationConsolidator.execute(ctx);
        model.addAttribute("message", result.success() ? result.summary() : "Consolidation skipped or failed");
        model.addAttribute("tone", result.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/books/{bookId}/chapter-consolidate-locations")
    public String consolidateBookLocations(@PathVariable UUID bookId, Model model) {
        var ctx = new StageExecutionContext(null, null, null, bookId, StageKey.BOOK_LOCATION_CONSOLIDATION);
        StageResult result = bookLocationConsolidator.execute(ctx);
        model.addAttribute("message", result.success() ? result.summary() : "Consolidation skipped or failed");
        model.addAttribute("tone", result.success() ? "success" : "info");
        return "ui/jobs :: actionToast";
    }

    @PostMapping("/chapters/{chapterId}/replay")
    public String replayChapter(@PathVariable UUID chapterId, Model model) {
        try {
            UUID jobId = pipelineCoordinator.findJobIdByChapterId(chapterId);
            if (jobId == null) {
                model.addAttribute("message", "No ingestion job found for this chapter.");
                model.addAttribute("tone", "error");
                return "ui/jobs :: actionToast";
            }
            UUID bookId = pipelineCoordinator.findBookIdByChapterId(chapterId);
            if (bookId == null) {
                model.addAttribute("message", "No book found for this chapter. Replay requires chapter-to-book association.");
                model.addAttribute("tone", "error");
                return "ui/jobs :: actionToast";
            }
            pipelineCoordinator.rerunStage(jobId, chapterId, bookId, StageKey.SCENE_SEGMENTATION);
            log.info("[REPLAY] Chapter replay initiated: chapterId={}, jobId={}", chapterId, jobId);
            model.addAttribute("message", "Replay initiated — destroying effects and reingesting chapter.");
            model.addAttribute("tone", "success");
        } catch (Exception e) {
            log.error("[REPLAY] Failed: chapterId={}: {}", chapterId, ExceptionSanitizer.sanitize(e), e);
            model.addAttribute("message", "Replay failed. Please try again.");
            model.addAttribute("tone", "error");
        }
        return "ui/jobs :: actionToast";
    }
}
