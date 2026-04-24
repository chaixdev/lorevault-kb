package com.lorevault.api.web.query.ask;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.search.domain.EntityLookupException;
import com.lorevault.api.search.domain.SemanticSearchException;
import com.lorevault.api.web.query.ask.AskDtos.AskMetadata;
import com.lorevault.api.web.query.ask.AskDtos.AskRequest;
import com.lorevault.api.web.query.ask.AskDtos.CitationDto;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.library.domain.PublicationCoordinates;
import com.lorevault.api.search.application.RagService;
import com.lorevault.api.search.application.SemanticSearchService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private SemanticSearchService semanticSearchService;

    @MockitoBean
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
        CoreSearchRecords.CoreSearchResult result = new CoreSearchRecords.CoreSearchResult(chunkId, 0.88, "Gandalf the Grey...", chapterId, 1, 1, UUID.randomUUID(), "Scene", List.of(), List.of());
        CoreSearchRecords.CoreSearchMetadata metadata = new CoreSearchRecords.CoreSearchMetadata("who is gandalf?", 1, 1, 12);
        CoreSearchRecords.CoreSemanticSearchResponse response = new CoreSearchRecords.CoreSemanticSearchResponse(List.of(result), metadata);

        Mockito.when(semanticSearchService.search(any(CoreSemanticSearchRequest.class))).thenReturn(response);

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
    void askVector_semanticSearchFailure_returns503() throws Exception {
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery("who is gandalf?");
        request.setTopK(3);

        IngestionFailure failure = IngestionFailure.builder(
                        "SEMANTIC_SEARCH_BACKEND_UNAVAILABLE",
                        "Search backend unavailable")
                .exceptionType(SemanticSearchException.class.getSimpleName())
                .stage("SEMANTIC_SEARCH")
                .build();
        Mockito.when(semanticSearchService.search(any()))
                .thenThrow(new SemanticSearchException(failure));

        mockMvc.perform(post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable());
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
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("Test Book");
        coordinates.setChapterTitle("Test Chapter");
        coordinates.setBookNumber(1);
        coordinates.setChapterNumber(1);
        CoreSearchRecords.CoreCitation citation = new CoreSearchRecords.CoreCitation(chunkId, 0.91, "wizard of middle-earth", coordinates);
        CoreSearchRecords.CoreAskMetadata metadata = new CoreSearchRecords.CoreAskMetadata("who is gandalf?", 3, 1, 25, "test-model");
        CoreSearchRecords.CoreAskResponse response = new CoreSearchRecords.CoreAskResponse("Gandalf is a wizard.", List.of(citation), metadata);

        Mockito.when(ragService.askRagBaseline(any())).thenReturn(response);

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

        Mockito.when(ragService.askRagBaseline(any())).thenThrow(new RuntimeException("boom"));

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

    @Test
    void askHybrid_success_returnsAnswerAndCitations() throws Exception {
        // Arrange
        AskRequest request = new AskRequest();
        request.setQuestion("who is vin?");
        request.setTopK(3);

        UUID chunkId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("Test Book");
        coordinates.setChapterTitle("Test Chapter");
        coordinates.setBookNumber(1);
        coordinates.setChapterNumber(1);
        CoreSearchRecords.CoreCitation coreCitation = new CoreSearchRecords.CoreCitation(chunkId, 0.028, "vin appears in both branches", coordinates);
        CoreSearchRecords.CoreAskMetadata coreMetadata = new CoreSearchRecords.CoreAskMetadata("who is vin?", 6, 3, 33, "test-model");
        CoreSearchRecords.CoreAskResponse response = new CoreSearchRecords.CoreAskResponse("Vin appears in both vector and graph evidence.", List.of(coreCitation), coreMetadata);

        Mockito.when(ragService.askHybrid(any(CoreAskRequest.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/query/ask/hybrid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer", containsString("vector and graph")))
            .andExpect(jsonPath("$.citations", hasSize(1)))
            .andExpect(jsonPath("$.citations[0].chunkId", is(chunkId.toString())))
            .andExpect(jsonPath("$.metadata.question", is("who is vin?")))
            .andExpect(jsonPath("$.metadata.chunksRetrieved", is(6)))
            .andExpect(jsonPath("$.metadata.chunksUsed", is(3)));
    }

    @Test
    void askHybrid_serviceThrows_returns500() throws Exception {
        AskRequest request = new AskRequest();
        request.setQuestion("who is vin?");
        request.setTopK(3);

        Mockito.when(ragService.askHybrid(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/query/ask/hybrid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void askGraphAware_success_returnsAnswerAndCitations() throws Exception {
        AskRequest request = new AskRequest();
        request.setQuestion("who is kelsier?");
        request.setTopK(3);

        UUID chunkId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("Test Book");
        coordinates.setChapterTitle("Test Chapter");
        coordinates.setBookNumber(1);
        coordinates.setChapterNumber(1);
        CoreSearchRecords.CoreCitation coreCitation = new CoreSearchRecords.CoreCitation(chunkId, 0.73, "kelsier leads the crew", coordinates);
        CoreSearchRecords.CoreAskMetadata coreMetadata = new CoreSearchRecords.CoreAskMetadata("who is kelsier?", 4, 2, 31, "test-model");
        CoreSearchRecords.CoreAskResponse response = new CoreSearchRecords.CoreAskResponse("Kelsier is the Survivor of Hathsin.", List.of(coreCitation), coreMetadata);

        Mockito.when(ragService.askGraphAware(any(CoreAskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/query/ask/graph-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Survivor")))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].chunkId", is(chunkId.toString())))
                .andExpect(jsonPath("$.metadata.question", is("who is kelsier?")));
    }

    @Test
    void askGraphAware_serviceThrows_returns500() throws Exception {
        AskRequest request = new AskRequest();
        request.setQuestion("who is kelsier?");
        request.setTopK(3);

        Mockito.when(ragService.askGraphAware(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/query/ask/graph-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void askGraphAware_entityLookupFailure_returns503() throws Exception {
        AskRequest request = new AskRequest();
        request.setQuestion("who is kelsier?");
        request.setTopK(3);

        IngestionFailure failure = IngestionFailure.builder(
                        "ENTITY_LOOKUP_QUERY_FAILED",
                        "Entity lookup query failed")
                .exceptionType(EntityLookupException.class.getSimpleName())
                .stage("ENTITY_LOOKUP")
                .build();
        Mockito.when(ragService.askGraphAware(any()))
                .thenThrow(new EntityLookupException(failure));

        mockMvc.perform(post("/api/query/ask/graph-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());
    }
}
