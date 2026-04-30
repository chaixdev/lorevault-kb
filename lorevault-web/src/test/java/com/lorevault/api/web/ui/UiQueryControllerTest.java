package com.lorevault.api.web.ui;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.library.service.LibraryQueryService;
import com.lorevault.api.search.model.CoreSearchRecords.*;

import com.lorevault.api.search.rag.RagService;
import com.lorevault.api.search.semantic.SemanticSearchService;
import com.lorevault.api.search.model.EntityLookupException;
import com.lorevault.api.search.model.SemanticSearchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @MockitoBean
    private LibraryQueryService libraryQueryService;

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

    private void stubLibraryOptions() {
        UUID universeId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID mistbornSeriesId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID stormlightSeriesId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        when(libraryQueryService.listUniverses()).thenReturn(List.of(
                new LibraryQueryService.UniverseSummary(universeId, "Cosmere")
        ));
        when(libraryQueryService.listSeries(universeId)).thenReturn(List.of(
                new LibraryQueryService.SeriesSummary(mistbornSeriesId, universeId, "Mistborn"),
                new LibraryQueryService.SeriesSummary(stormlightSeriesId, universeId, "Stormlight")
        ));
    }

    private void stubStandaloneBookChapters() {
        UUID bookId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        when(libraryQueryService.listChaptersForBook(bookId)).thenReturn(List.of(
                new LibraryQueryService.ChapterSummary(
                        UUID.fromString("00000000-0000-0000-0000-000000000401"),
                        8,
                        0,
                        "008 Deathworlders Taking Back The Sky",
                        3
                )
        ));
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
    void vectorEndpointSemanticSearchFailureReturnsErrorFragment() throws Exception {
        when(semanticSearchService.search(any())).thenThrow(new SemanticSearchException(
                IngestionFailure.builder("SEMANTIC_SEARCH_BACKEND_UNAVAILABLE", "Semantic search unavailable")
                        .exceptionType("SemanticSearchException")
                        .stage("SEMANTIC_SEARCH")
                        .build()
        ));

        mockMvc.perform(post("/ui/query/ask/vector")
                        .param("question", "Who is Vin?")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Search is temporarily unavailable. Please try again.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vector retrieval")));
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
    void ragEndpointBuildsSpoilerVisibilityFromReadingProgressFixture() throws Exception {
        stubLibraryOptions();
        when(ragService.askRagBaseline(any())).thenReturn(response("spoiler scoped answer"));

        mockMvc.perform(post("/ui/query/ask/rag")
                        .param("question", "What does Vin know?")
                        .param("topK", "5")
                        .param("universeId", "00000000-0000-0000-0000-000000000101")
                        .param("seriesId", "00000000-0000-0000-0000-000000000201")
                        .param("readThroughBookNumber", "1")
                        .param("readThroughChapterNumber", "12")
                        .param("unconfiguredSeriesPolicy", "SHOW"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spoiler scoped answer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cosmere • Mistborn • book 1 (chapter 12) • unconfigured SHOW")));

        ArgumentCaptor<CoreAskRequest> requestCaptor = ArgumentCaptor.forClass(CoreAskRequest.class);
        verify(ragService).askRagBaseline(requestCaptor.capture());

        CoreAskRequest request = requestCaptor.getValue();
        assertThat(request.visibility()).isNotNull();
        assertThat(request.visibility().getUniverse()).isEqualTo("Cosmere");
        assertThat(request.visibility().getUnconfiguredSeriesPolicy().name()).isEqualTo("SHOW");
        assertThat(request.visibility().getSeriesProgress()).hasSize(1);
        assertThat(request.visibility().getSeriesProgress().getFirst().getSeries()).isEqualTo("Mistborn");
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughBookNumber()).isEqualTo(1);
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughChapterNumber()).isEqualTo(12);
    }

    @Test
    void ragEndpointTreatsBlankChapterAsFullBookProgress() throws Exception {
        stubLibraryOptions();
        when(ragService.askRagBaseline(any())).thenReturn(response("full book answer"));

        mockMvc.perform(post("/ui/query/ask/rag")
                        .param("question", "What does Vin know?")
                        .param("topK", "5")
                        .param("universeId", "00000000-0000-0000-0000-000000000101")
                        .param("seriesId", "00000000-0000-0000-0000-000000000201")
                        .param("readThroughBookNumber", "1")
                        .param("readThroughChapterNumber", "")
                        .param("unconfiguredSeriesPolicy", "HIDE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cosmere • Mistborn • book 1 (full book) • unconfigured HIDE")));

        ArgumentCaptor<CoreAskRequest> requestCaptor = ArgumentCaptor.forClass(CoreAskRequest.class);
        verify(ragService).askRagBaseline(requestCaptor.capture());

        CoreAskRequest request = requestCaptor.getValue();
        assertThat(request.visibility()).isNotNull();
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughChapterNumber()).isNull();
    }

    @Test
    void ragEndpointBuildsStandaloneVisibilityFromSelectedBookWhenBookNumberAndSeriesAreBlank() throws Exception {
        stubLibraryOptions();
        stubStandaloneBookChapters();
        when(ragService.askRagBaseline(any())).thenReturn(response("standalone scoped answer"));

        mockMvc.perform(post("/ui/query/ask/rag")
                        .param("question", "who's adam")
                        .param("topK", "5")
                        .param("universeId", "00000000-0000-0000-0000-000000000101")
                        .param("series", "")
                        .param("seriesId", "")
                        .param("bookId", "00000000-0000-0000-0000-000000000301")
                        .param("readThroughBookNumber", "")
                        .param("readThroughChapterNumber", "8")
                        .param("unconfiguredSeriesPolicy", "HIDE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("standalone scoped answer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cosmere • Standalone • book 0 (chapter 8) • unconfigured HIDE")));

        ArgumentCaptor<CoreAskRequest> requestCaptor = ArgumentCaptor.forClass(CoreAskRequest.class);
        verify(ragService).askRagBaseline(requestCaptor.capture());

        CoreAskRequest request = requestCaptor.getValue();
        assertThat(request.visibility()).isNotNull();
        assertThat(request.visibility().getUniverse()).isEqualTo("Cosmere");
        assertThat(request.visibility().getSeriesProgress().getFirst().getSeries()).isNull();
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughBookNumber()).isZero();
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughChapterNumber()).isEqualTo(8);
    }

    @Test
    void vectorEndpointBuildsSpoilerVisibilityFromReadingProgressFixture() throws Exception {
        stubLibraryOptions();
        when(semanticSearchService.search(any())).thenReturn(vectorResponse("safe vector snippet"));

        mockMvc.perform(post("/ui/query/ask/vector")
                        .param("question", "Find safe context")
                        .param("topK", "5")
                        .param("universeId", "00000000-0000-0000-0000-000000000101")
                        .param("seriesId", "00000000-0000-0000-0000-000000000202")
                        .param("readThroughBookNumber", "2")
                        .param("unconfiguredSeriesPolicy", "HIDE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("safe vector snippet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cosmere • Stormlight • book 2 (full book) • unconfigured HIDE")));

        ArgumentCaptor<CoreSemanticSearchRequest> requestCaptor = ArgumentCaptor.forClass(CoreSemanticSearchRequest.class);
        verify(semanticSearchService).search(requestCaptor.capture());

        CoreSemanticSearchRequest request = requestCaptor.getValue();
        assertThat(request.visibility()).isNotNull();
        assertThat(request.visibility().getUniverse()).isEqualTo("Cosmere");
        assertThat(request.visibility().getSeriesProgress().getFirst().getSeries()).isEqualTo("Stormlight");
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughBookNumber()).isEqualTo(2);
        assertThat(request.visibility().getSeriesProgress().getFirst().getReadThroughChapterNumber()).isNull();
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
    void graphAwareEndpointEntityLookupFailureReturnsErrorFragment() throws Exception {
        when(ragService.askGraphAware(any())).thenThrow(new EntityLookupException(
                IngestionFailure.builder("ENTITY_LOOKUP_QUERY_FAILED", "Entity lookup unavailable")
                        .exceptionType("EntityLookupException")
                        .stage("ENTITY_LOOKUP")
                        .build()
        ));

        mockMvc.perform(post("/ui/query/ask/graph-aware")
                        .param("question", "Who is Kelsier?")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Search is temporarily unavailable. Please try again.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Graph-aware")));
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
