package com.lorevault.api.web.command.ingestion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lorevault.api.ingestion.resolution.collective.BookCollectiveReductionService;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveResolutionResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BookCollectiveResolutionCommandController.class)
class BookCollectiveResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookCollectiveReductionService bookCollectiveReductionService;

    @Test
    void resolveBookCollectivesSuccessReturns200() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookCollectiveReductionService.bookExists(bookId)).thenReturn(true);
        when(bookCollectiveReductionService.resolveBook(bookId))
                .thenReturn(new BookCollectiveResolutionResult(bookId, true, 2, 1, "Resolved book collectives"));

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-collectives", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.chapterCollectiveCount").value(2))
                .andExpect(jsonPath("$.bookCollectiveCount").value(1));

        verify(bookCollectiveReductionService).resolveBook(bookId);
    }

    @Test
    void resolveBookCollectivesInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/books/not-a-uuid/resolve-collectives"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BOOK_ID"));
    }

    @Test
    void resolveBookCollectivesMissingBookReturns404() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookCollectiveReductionService.bookExists(bookId)).thenReturn(false);

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-collectives", bookId))
                .andExpect(status().isNotFound());
    }
}
