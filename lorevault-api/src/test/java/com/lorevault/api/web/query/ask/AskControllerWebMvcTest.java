package com.lorevault.api.web.query.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.dto.ask.AskDtos.AskMetadata;
import com.lorevault.api.dto.ask.AskDtos.AskRequest;
import com.lorevault.api.dto.ask.AskDtos.AskResponse;
import com.lorevault.api.dto.ask.AskDtos.CitationDto;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchMetadata;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.service.ask.RagService;
import com.lorevault.api.service.search.SemanticSearchService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AskController.class)
@Tag("controller")
@Execution(ExecutionMode.SAME_THREAD)
class AskControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SemanticSearchService semanticSearchService;

    @MockBean
    private RagService ragService;

    @AfterEach
    void resetMocks() { Mockito.reset(semanticSearchService, ragService); }

    @Test
    void askVector_success_returnsResults() throws Exception {
        // Arrange
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery("who is gandalf?");
        request.setTopK(3);

        UUID chunkId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID chapterId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        SearchResultDto result = SearchResultDto.of(chunkId, 0.88, "Gandalf the Grey...", chapterId, 1, 1);
        SearchMetadata metadata = SearchMetadata.of("who is gandalf?", 1, 1, 12);
        SemanticSearchResponse response = SemanticSearchResponse.of(List.of(result), metadata);

        Mockito.when(semanticSearchService.search(any())).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].chunkId", is(chunkId.toString())))
            .andExpect(jsonPath("$.results[0].score", is(closeTo(0.88, 1e-6))))
            .andExpect(jsonPath("$.results[0].snippet", containsString("Gandalf")))
            .andExpect(jsonPath("$.metadata.query", is("who is gandalf?")))
            .andExpect(jsonPath("$.metadata.totalResults", is(1)))
            .andExpect(jsonPath("$.metadata.returnedResults", is(1)))
            .andExpect(jsonPath("$.metadata.processingTimeMs", is(12)));
    }

    @Test
    void askVector_serviceThrows_returns500() throws Exception {
        // Arrange
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery("who is gandalf?");
        request.setTopK(3);

        Mockito.when(semanticSearchService.search(any())).thenThrow(new RuntimeException("boom"));

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void askVector_validationError_returns400() throws Exception {
        // Arrange: blank query violates @NotBlank
        SemanticSearchRequest bad = new SemanticSearchRequest();
        bad.setQuery("");
        bad.setTopK(3);

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void askRag_success_returnsAnswerAndCitations() throws Exception {
        // Arrange
        AskRequest request = new AskRequest();
        request.setQuestion("who is gandalf?");
        request.setTopK(3);

        UUID chunkId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID chapterId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        CitationDto citation = CitationDto.of(chunkId, 0.91, "wizard of middle-earth", chapterId, 1, 1);
        AskMetadata metadata = AskMetadata.of("who is gandalf?", 3, 1, 25, "test-model");
        AskResponse response = AskResponse.of("Gandalf is a wizard.", List.of(citation), metadata);

        Mockito.when(ragService.ask(any())).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer", containsString("wizard")))
            .andExpect(jsonPath("$.citations", hasSize(1)))
            .andExpect(jsonPath("$.citations[0].chunkId", is(chunkId.toString())))
            .andExpect(jsonPath("$.metadata.modelId", is("test-model")))
            .andExpect(jsonPath("$.metadata.question", is("who is gandalf?")))
            .andExpect(jsonPath("$.metadata.chunksRetrieved", is(3)))
            .andExpect(jsonPath("$.metadata.chunksUsed", is(1)))
            .andExpect(jsonPath("$.metadata.processingTimeMs", is(25)));
    }

    @Test
    void askRag_serviceThrows_returns500() throws Exception {
        // Arrange
        AskRequest request = new AskRequest();
        request.setQuestion("who is gandalf?");
        request.setTopK(3);

        Mockito.when(ragService.ask(any())).thenThrow(new RuntimeException("boom"));

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void askRag_validationError_returns400() throws Exception {
        // Arrange: blank question violates @NotBlank
        AskRequest bad = new AskRequest();
        bad.setQuestion("");
        bad.setTopK(3);

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
            .andExpect(status().isBadRequest());
    }
}
