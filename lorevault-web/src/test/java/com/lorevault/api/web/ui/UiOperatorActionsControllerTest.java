package com.lorevault.api.web.ui;

import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionResult;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionService;
import com.lorevault.api.ingestion.resolution.location.BookLocationReductionService;
import com.lorevault.api.ingestion.resolution.location.BookLocationResolutionResult;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationResolutionResult;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationResolutionService;
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
    private ChapterIndividualResolutionService chapterIndividualResolutionService;

    @MockitoBean
    private ChapterLocationResolutionService chapterLocationResolutionService;

    @MockitoBean
    private BookLocationReductionService bookLocationReductionService;

    @Test
    void chapterIndividualActionReturnsToast() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterIndividualResolutionResult(chapterId, true, 3, 2, "Resolved chapter individuals"));

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/resolve-individuals"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter individuals")));
    }

    @Test
    void chapterLocationActionReturnsToast() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterLocationResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterLocationResolutionResult(chapterId, true, 2, 1, "Resolved chapter locations"));

        mockMvc.perform(post("/ui/actions/chapters/" + chapterId + "/resolve-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved chapter locations")));
    }

    @Test
    void bookLocationActionReturnsToast() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookLocationReductionService.resolveBook(bookId))
                .thenReturn(new BookLocationResolutionResult(bookId, true, 4, 2, "Resolved book-level locations"));

        mockMvc.perform(post("/ui/actions/books/" + bookId + "/resolve-locations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resolved book-level locations")));
    }
}
