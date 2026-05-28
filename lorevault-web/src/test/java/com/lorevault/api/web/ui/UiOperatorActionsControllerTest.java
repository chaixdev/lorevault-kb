package com.lorevault.api.web.ui;

import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualConsolidationResult;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualConsolidationService;
import com.lorevault.api.ingestion.resolution.location.BookLocationConsolidationService;
import com.lorevault.api.ingestion.resolution.location.BookLocationConsolidationResult;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationConsolidationResult;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationConsolidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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

    @Test
    void chapterIndividualActionReturnsToast() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualConsolidationService.consolidateChapter(chapterId))
                .thenReturn(new ChapterIndividualConsolidationResult(chapterId, true, 3, 2, "Resolved chapter individuals"));

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/chapter-consolidate-individuals"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter individuals")));
    }

    @Test
    void chapterLocationActionReturnsToast() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterLocationConsolidationService.consolidateChapter(chapterId))
                .thenReturn(new ChapterLocationConsolidationResult(chapterId, true, 2, 1, "Resolved chapter locations"));

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/chapter-consolidate-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter locations")));
    }

    @Test
    void bookLocationActionReturnsToast() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookLocationConsolidationService.consolidateBook(bookId))
                .thenReturn(new BookLocationConsolidationResult(bookId, true, 4, 2, "Resolved book-level locations"));

        mockMvc.perform(post("/ui/actions/books/" + bookId + "/chapter-consolidate-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved book-level locations")));
    }
}
