package com.lorevault.api.web.ui;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.web.query.ask.AskDtos;
import com.lorevault.api.search.application.RagService;
import com.lorevault.api.web.query.ask.SemanticSearchDtos;
import com.lorevault.api.search.application.SemanticSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiQueryController.class)
@DisplayName("UiQueryController tests")
class UiQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private SemanticSearchService semanticSearchService;

    private CoreAskResponse response(String answer) {
        CoreAskMetadata metadata = new CoreAskMetadata("q", 3, 2, 20, "test-model");
        return new CoreAskResponse(answer, List.of(), metadata);
    }

    private CoreSemanticSearchResponse vectorResponse(String snippet) {
        UUID chunkId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        CoreSearchResult result = new CoreSearchResult(chunkId, 0.91, snippet, null, 1, 1, null, null, List.of(), List.of());
        CoreSearchMetadata metadata = new CoreSearchMetadata("q", 1, 1, 10);
        return new CoreSemanticSearchResponse(List.of(result), metadata);
    }

    @Test
    void vectorEndpointUsesSemanticSearchService() throws Exception {
        when(semanticSearchService.search(any())).thenReturn(vectorResponse("vector snippet"));

        mockMvc.perform(post("/ui/query/ask/vector")
                        .param("question", "Who is Vin?")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("vector snippet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vector retrieval")));

        verify(semanticSearchService).search(any());
    }

    @Test
    void ragEndpointUsesBaselineMethod() throws Exception {
        when(ragService.askRagBaseline(any())).thenReturn(response("rag baseline"));

        mockMvc.perform(post("/ui/query/ask/rag")
                        .param("question", "Who is Vin?")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rag baseline")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RAG baseline")));

        verify(ragService).askRagBaseline(any());
    }

    @Test
    void graphAwareEndpointUsesGraphAwareMethod() throws Exception {
        when(ragService.askGraphAware(any())).thenReturn(response("graph aware"));

        mockMvc.perform(post("/ui/query/ask/graph-aware")
                        .param("question", "Who is Kelsier?")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("graph aware")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Graph-aware")));

        verify(ragService).askGraphAware(any());
    }

    @Test
    void hybridEndpointUsesHybridMethod() throws Exception {
        when(ragService.askHybrid(any())).thenReturn(response("hybrid"));

        mockMvc.perform(post("/ui/query/ask/hybrid")
                        .param("question", "Who is Vin?")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hybrid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hybrid RRF")));

        verify(ragService).askHybrid(any());
    }
}
