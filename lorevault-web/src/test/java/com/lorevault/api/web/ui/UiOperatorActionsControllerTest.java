package com.lorevault.api.web.ui;

import com.lorevault.api.graph.individual.consolidation.chapter.ChapterIndividualConsolidationHandler;
import com.lorevault.api.graph.location.consolidation.chapter.ChapterLocationConsolidationHandler;
import com.lorevault.api.graph.location.consolidation.book.BookLocationConsolidationHandler;
import com.lorevault.api.orchestration.pipeline.IngestionPipelineCoordinator;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
    private ChapterIndividualConsolidationHandler chapterIndividualConsolidator;

    @MockitoBean
    private ChapterLocationConsolidationHandler chapterLocationConsolidator;

    @MockitoBean
    private BookLocationConsolidationHandler bookLocationConsolidator;

    @MockitoBean
    private IngestionPipelineCoordinator pipelineCoordinator;

    @Test
    void chapterIndividualActionReturnsToast() throws Exception {
        when(chapterIndividualConsolidator.execute(any(StageExecutionContext.class)))
                .thenReturn(StageResult.success(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, "Resolved chapter individuals", 150L));
        UUID chapterId = UUID.randomUUID();

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/chapter-consolidate-individuals"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter individuals")));
    }

    @Test
    void chapterLocationActionReturnsToast() throws Exception {
        when(chapterLocationConsolidator.execute(any(StageExecutionContext.class)))
                .thenReturn(StageResult.success(StageKey.CHAPTER_LOCATION_CONSOLIDATION, "Resolved chapter locations", 150L));
        UUID chapterId = UUID.randomUUID();

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/chapter-consolidate-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter locations")));
    }

    @Test
    void bookLocationActionReturnsToast() throws Exception {
        when(bookLocationConsolidator.execute(any(StageExecutionContext.class)))
                .thenReturn(StageResult.success(StageKey.BOOK_LOCATION_CONSOLIDATION, "Resolved book-level locations", 150L));
        UUID bookId = UUID.randomUUID();

        mockMvc.perform(post("/ui/actions/books/" + bookId + "/chapter-consolidate-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved book-level locations")));
    }

    @Test
    void consolidationFailureShowsInfoTone() throws Exception {
        when(chapterIndividualConsolidator.execute(any(StageExecutionContext.class)))
                .thenReturn(StageResult.failure(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, "No mentions found", 0L));

        mockMvc.perform(post("/ui/actions/chapters/" + UUID.randomUUID() + "/chapter-consolidate-individuals"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Consolidation skipped or failed")));
    }
}
