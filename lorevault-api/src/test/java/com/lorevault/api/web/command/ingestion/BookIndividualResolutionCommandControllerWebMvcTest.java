package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.Book;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.ingestion.BookIndividualReductionService;
import com.lorevault.api.support.BookIndividualResolutionResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookIndividualResolutionCommandController.class)
class BookIndividualResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookGraphRepository bookGraphRepository;

    @MockitoBean
    private BookIndividualReductionService bookIndividualReductionService;

    @Test
    void resolveBookIndividuals_success_returns200() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookGraphRepository.findById(bookId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Book.class)));
        when(bookIndividualReductionService.resolveBook(bookId))
                .thenReturn(new BookIndividualResolutionResponse(bookId, true, 2, 2, "Resolved book-level individuals"));

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-individuals", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.chapterIndividualCount").value(2))
                .andExpect(jsonPath("$.bookIndividualCount").value(2));

        verify(bookIndividualReductionService).resolveBook(bookId);
    }

    @Test
    void resolveBookIndividuals_invalidUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/books/not-a-uuid/resolve-individuals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BOOK_ID"));
    }

    @Test
    void resolveBookIndividuals_missingBook_returns404() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookGraphRepository.findById(bookId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-individuals", bookId))
                .andExpect(status().isNotFound());
    }
}
