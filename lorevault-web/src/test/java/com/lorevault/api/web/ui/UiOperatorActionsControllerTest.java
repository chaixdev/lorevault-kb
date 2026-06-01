package com.lorevault.api.web.ui;

import com.lorevault.api.orchestration.pipeline.IngestionPipelineCoordinator;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationResult;
import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationService;
import com.lorevault.api.graph.location.consolidation.book.BookLocationConsolidationService;
import com.lorevault.api.graph.location.consolidation.book.BookLocationConsolidationResult;
import com.lorevault.api.graph.location.consolidation.chapter.ChapterLocationConsolidationResult;
import com.lorevault.api.graph.location.consolidation.chapter.ChapterLocationConsolidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiOperatorActionsController.class)
@DisplayName("UiOperatorActionsController tests")
class UiOperatorActionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterIndividualConsolidationService chapterIndividualConsolidationService;

    @MockitoBean
    private ChapterLocationConsolidationService chapterLocationConsolidationService;

    @MockitoBean
    private BookLocationConsolidationService bookLocationConsolidationService;

    @MockitoBean
    private IngestionPipelineCoordinator pipelineCoordinator;

    @Test
    void chapterIndividualActionReturnsToast() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualConsolidationService.consolidateChapter(any(StageExecutionContext.class), eq(chapterId)))
                .thenReturn(new ChapterIndividualConsolidationResult(chapterId, true, 3, 2, "Resolved chapter individuals"));

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/chapter-consolidate-individuals"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter individuals")));
    }

    @Test
    void chapterLocationActionReturnsToast() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterLocationConsolidationService.consolidateChapter(any(StageExecutionContext.class), eq(chapterId)))
                .thenReturn(new ChapterLocationConsolidationResult(chapterId, true, 2, 1, "Resolved chapter locations"));

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/chapter-consolidate-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter locations")));
    }

    @Test
    void bookLocationActionReturnsToast() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookLocationConsolidationService.consolidateBook(any(StageExecutionContext.class), eq(bookId)))
                .thenReturn(new BookLocationConsolidationResult(bookId, true, 4, 2, "Resolved book-level locations"));

        mockMvc.perform(post("/ui/actions/books/" + bookId + "/chapter-consolidate-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved book-level locations")));
    }
}
