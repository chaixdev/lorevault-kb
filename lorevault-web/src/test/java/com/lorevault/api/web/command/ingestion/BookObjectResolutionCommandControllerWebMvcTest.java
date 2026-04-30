package com.lorevault.api.web.command.ingestion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lorevault.api.ingestion.resolution.object.BookObjectReductionService;
import com.lorevault.api.ingestion.resolution.object.BookObjectResolutionResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BookObjectResolutionCommandController.class)
class BookObjectResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookObjectReductionService bookObjectReductionService;

    @Test
    void resolveBookObjectsSuccessReturns200() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookObjectReductionService.bookExists(bookId)).thenReturn(true);
        when(bookObjectReductionService.resolveBook(bookId))
                .thenReturn(new BookObjectResolutionResult(bookId, true, 2, 1, "Resolved book objects"));

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-objects", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.chapterObjectCount").value(2))
                .andExpect(jsonPath("$.bookObjectCount").value(1));

        verify(bookObjectReductionService).resolveBook(bookId);
    }

    @Test
    void resolveBookObjectsInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/books/not-a-uuid/resolve-objects"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BOOK_ID"));
    }

    @Test
    void resolveBookObjectsMissingBookReturns404() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookObjectReductionService.bookExists(bookId)).thenReturn(false);

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-objects", bookId))
                .andExpect(status().isNotFound());
    }
}
