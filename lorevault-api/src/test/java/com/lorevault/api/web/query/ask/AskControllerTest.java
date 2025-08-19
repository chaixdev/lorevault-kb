package com.lorevault.api.web.query.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.dto.ask.AskDtos.AskRequest;
import com.lorevault.api.dto.ask.AskDtos.AskResponse;
import com.lorevault.api.dto.ask.AskDtos.CitationDto;
import com.lorevault.api.dto.ask.AskDtos.AskMetadata;
import com.lorevault.api.service.ask.RagService;
import com.lorevault.api.service.search.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(AskController.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SemanticSearchService semanticSearchService;

    @MockBean
    private RagService ragService;

    @Test
    void askRag_WithValidRequest_ReturnsAskResponse() throws Exception {
        // Given
        AskRequest request = new AskRequest();
        request.setQuestion("Who is Kaladin?");
        request.setTopK(5);

        List<CitationDto> citations = List.of(
            CitationDto.of(UUID.randomUUID(), 0.92, "Kaladin is a bridgeman", 
                          UUID.randomUUID(), 1, 4)
        );
        
        AskMetadata metadata = AskMetadata.of("Who is Kaladin?", 3, 1, 250L, "llama-3.1-70b-versatile");
        AskResponse mockResponse = AskResponse.of("Kaladin is a Windrunner", citations, metadata);

        when(ragService.ask(any(AskRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value("Kaladin is a Windrunner"))
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations[0].score").value(0.92))
                .andExpect(jsonPath("$.citations[0].snippet").value("Kaladin is a bridgeman"))
                .andExpect(jsonPath("$.citations[0].bookNumber").value(1))
                .andExpect(jsonPath("$.citations[0].chapterNumber").value(4))
                .andExpect(jsonPath("$.metadata.question").value("Who is Kaladin?"))
                .andExpect(jsonPath("$.metadata.chunksRetrieved").value(3))
                .andExpect(jsonPath("$.metadata.chunksUsed").value(1))
                .andExpect(jsonPath("$.metadata.processingTimeMs").value(250))
                .andExpect(jsonPath("$.metadata.modelId").value("llama-3.1-70b-versatile"));
    }

    @Test
    void askRag_WithInvalidRequest_ReturnsBadRequest() throws Exception {
        // Given
        AskRequest invalidRequest = new AskRequest();
        // Missing required question field

        // When & Then
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askRag_WithServiceException_ReturnsInternalServerError() throws Exception {
        // Given
        AskRequest request = new AskRequest();
        request.setQuestion("Who is Kaladin?");
        request.setTopK(5);

        when(ragService.ask(any(AskRequest.class)))
            .thenThrow(new RuntimeException("Search service unavailable"));

        // When & Then
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void askRag_WithTopKOutOfBounds_ReturnsBadRequest() throws Exception {
        // Given
        AskRequest request = new AskRequest();
        request.setQuestion("Who is Kaladin?");
        request.setTopK(50); // Exceeds @Max(10)

        // When & Then
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askRag_WithThresholdOutOfBounds_ReturnsBadRequest() throws Exception {
        // Given
        AskRequest request = new AskRequest();
        request.setQuestion("Who is Kaladin?");
        request.setThreshold(1.5); // Exceeds @Max(1.0)

        // When & Then
        mockMvc.perform(post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
