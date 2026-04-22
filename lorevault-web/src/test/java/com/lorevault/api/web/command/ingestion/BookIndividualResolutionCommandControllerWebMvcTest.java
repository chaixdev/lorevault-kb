package com.lorevault.api.web.command.ingestion;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.ingestion.application.BookIndividualReductionService;
import com.lorevault.api.web.command.ingestion.BookIndividualResolutionResponse;
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
    private BookIndividualReductionService bookIndividualReductionService;

    @Test
    void resolveBookIndividuals_success_returns200() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(bookIndividualReductionService.bookExists(bookId)).thenReturn(true);
        when(bookIndividualReductionService.resolveBook(bookId))
                .thenReturn(new BookIndividualResolutionResult(bookId, true, 2, 2, "Resolved book-level individuals"));

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
        when(bookIndividualReductionService.bookExists(bookId)).thenReturn(false);

        mockMvc.perform(post("/api/command/ingest/books/{bookId}/resolve-individuals", bookId))
                .andExpect(status().isNotFound());
    }
}
