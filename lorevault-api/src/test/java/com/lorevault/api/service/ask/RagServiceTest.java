package com.lorevault.api.service.ask;

import com.lorevault.api.dto.ask.AskDtos.AskRequest;
import com.lorevault.api.dto.ask.AskDtos.AskResponse;
import com.lorevault.api.dto.ask.AskDtos.AskFilters;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchMetadata;
import com.lorevault.api.service.search.SemanticSearchService;
import com.lorevault.api.service.shared.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagServiceTest {

    @Mock
    private SemanticSearchService semanticSearchService;
    
    @Mock
    private ChatClient chatClient;
    
    @Mock
    private PromptLoaderService promptLoaderService;
    
    @InjectMocks
    private RagService ragService;

    private AskRequest request;
    private SemanticSearchResponse searchResponse;

    @BeforeEach
    void setUp() {
        request = new AskRequest();
        request.setQuestion("Who is Kaladin?");
        request.setTopK(3);
        
        // Mock search results with chapter-level metadata
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();
        UUID chapterId1 = UUID.randomUUID();
        UUID chapterId2 = UUID.randomUUID();
        
        List<SearchResultDto> searchResults = List.of(
            SearchResultDto.of(chunkId1, 0.92, "Kaladin is a bridgeman turned Windrunner", chapterId1, 1, 4),
            SearchResultDto.of(chunkId2, 0.85, "He leads Bridge Four with honor", chapterId2, 1, 8)
        );
        
        SearchMetadata searchMetadata = SearchMetadata.of("Who is Kaladin?", 2, 2, 150L);
        searchResponse = SemanticSearchResponse.of(searchResults, searchMetadata);
        
        // Mock prompt template
        PromptTemplate mockPromptTemplate = mock(PromptTemplate.class);
        when(mockPromptTemplate.render(any())).thenReturn("You are a knowledgeable assistant. Answer based on the provided context.");
        when(promptLoaderService.getRagAnswerGenerationPromptTemplate()).thenReturn(mockPromptTemplate);
    }

    @Test
    void ask_WithValidQuestion_ReturnsAnswerWithCitations() {
        // Given
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        // Override the default ChatClient response for this test
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(callSpec.content()).thenReturn("Kaladin is a Windrunner and former bridgeman who leads Bridge Four.");
        
        // Reset the mock for this specific test
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(chatClient.prompt()).thenReturn(requestSpec);

        // When
        AskResponse response = ragService.ask(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isEqualTo("Kaladin is a Windrunner and former bridgeman who leads Bridge Four.");
        
        assertThat(response.getCitations()).hasSize(2);
        assertThat(response.getCitations().get(0).getScore()).isEqualTo(0.92);
        assertThat(response.getCitations().get(0).getSnippet()).isEqualTo("Kaladin is a bridgeman turned Windrunner");
        assertThat(response.getCitations().get(0).getBookNumber()).isEqualTo(1);
        assertThat(response.getCitations().get(0).getChapterNumber()).isEqualTo(4);
        
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getQuestion()).isEqualTo("Who is Kaladin?");
        assertThat(response.getMetadata().getChunksRetrieved()).isEqualTo(2);
        assertThat(response.getMetadata().getChunksUsed()).isEqualTo(2);
        assertThat(response.getMetadata().getModelId()).isNotBlank();

        // Verify search was called with correct parameters
        verify(semanticSearchService).search(argThat(searchRequest -> 
            searchRequest.getQuery().equals("Who is Kaladin?") &&
            searchRequest.getTopK().equals(3)
        ));
        
        // Verify LLM was called with system and user prompts
        verify(requestSpec).system(contains("answer the question"));
        verify(requestSpec).user(contains("Who is Kaladin?"));
    }

    @Test
    void ask_WithValidFilters_PassesFiltersToSearch() {
        // Given
        AskFilters filters = new AskFilters();
        filters.setUniverse("Cosmere");
        filters.setSeries("Stormlight Archive");
        filters.setBookNumber(1);
        filters.setChapterNumber(4);
        request.setFilters(filters);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Test answer");

        // When
        ragService.ask(request);

        // Then - all filters should be passed through when hierarchy is valid
        verify(semanticSearchService).search(argThat(searchRequest -> {
            var searchFilters = searchRequest.getFilters();
            return searchFilters != null &&
                   "Cosmere".equals(searchFilters.getUniverse()) &&
                   "Stormlight Archive".equals(searchFilters.getSeries()) &&
                   Integer.valueOf(1).equals(searchFilters.getBookNumber()) &&
                   Integer.valueOf(4).equals(searchFilters.getChapterNumber());
        }));
    }

    @Test
    void ask_WithInvalidFilterHierarchy_FiltersInvalidLevels() {
        // Given - chapter specified without book (invalid)
        AskFilters filters = new AskFilters();
        filters.setUniverse("Cosmere");
        filters.setChapterNumber(4); // Invalid: no book specified
        request.setFilters(filters);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Test answer");

        // When
        ragService.ask(request);

        // Then - only universe should be passed, chapter should be filtered out
        verify(semanticSearchService).search(argThat(searchRequest -> {
            var searchFilters = searchRequest.getFilters();
            return searchFilters != null &&
                   "Cosmere".equals(searchFilters.getUniverse()) &&
                   searchFilters.getSeries() == null &&
                   searchFilters.getBookNumber() == null &&
                   searchFilters.getChapterNumber() == null;
        }));
    }

    @Test
    void ask_WithBookButNoSeries_FiltersBookAndChapter() {
        // Given - book specified without series (invalid)
        AskFilters filters = new AskFilters();
        filters.setUniverse("Cosmere");
        filters.setBookNumber(1); // Invalid: no series specified
        filters.setChapterNumber(4); // Should also be filtered out
        request.setFilters(filters);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Test answer");

        // When
        ragService.ask(request);

        // Then - only universe should remain
        verify(semanticSearchService).search(argThat(searchRequest -> {
            var searchFilters = searchRequest.getFilters();
            return searchFilters != null &&
                   "Cosmere".equals(searchFilters.getUniverse()) &&
                   searchFilters.getSeries() == null &&
                   searchFilters.getBookNumber() == null &&
                   searchFilters.getChapterNumber() == null;
        }));
    }

    @Test
    void ask_WithSeriesButNoUniverse_FiltersAllLevels() {
        // Given - series specified without universe (invalid)
        AskFilters filters = new AskFilters();
        filters.setSeries("Stormlight Archive"); // Invalid: no universe specified
        filters.setBookNumber(1); // Should be filtered out
        filters.setChapterNumber(4); // Should be filtered out
        request.setFilters(filters);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Test answer");

        // When
        ragService.ask(request);

        // Then - no filters should be applied (null filters)
        verify(semanticSearchService).search(argThat(searchRequest -> 
            searchRequest.getFilters() == null
        ));
    }

    @Test
    void ask_WithPartialValidFilters_PassesValidLevelsOnly() {
        // Given - valid hierarchy up to book level
        AskFilters filters = new AskFilters();
        filters.setUniverse("Cosmere");
        filters.setSeries("Stormlight Archive");
        filters.setBookNumber(1);
        // No chapter - should include whole book
        request.setFilters(filters);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Test answer");

        // When
        ragService.ask(request);

        // Then - universe, series, and book should be passed
        verify(semanticSearchService).search(argThat(searchRequest -> {
            var searchFilters = searchRequest.getFilters();
            return searchFilters != null &&
                   "Cosmere".equals(searchFilters.getUniverse()) &&
                   "Stormlight Archive".equals(searchFilters.getSeries()) &&
                   Integer.valueOf(1).equals(searchFilters.getBookNumber()) &&
                   searchFilters.getChapterNumber() == null; // No chapter specified
        }));
    }

    @Test
    void ask_WithNoSearchResults_ReturnsNoEvidenceAnswer() {
        // Given
        SearchMetadata emptyMetadata = SearchMetadata.of("Who is Navani?", 0, 0, 50L);
        SemanticSearchResponse emptyResponse = SemanticSearchResponse.of(List.of(), emptyMetadata);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(emptyResponse);

        // When
        AskResponse response = ragService.ask(request);

        // Then
        assertThat(response.getAnswer()).contains("No evidence found");
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getMetadata().getChunksRetrieved()).isZero();
        assertThat(response.getMetadata().getChunksUsed()).isZero();
        
        // Verify LLM was not called when no evidence
        verify(chatClient, never()).prompt();
    }

    @Test
    void ask_WithSearchFailure_PropagatesException() {
        // Given
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenThrow(new RuntimeException("Search service unavailable"));

        // When/Then
        assertThatThrownBy(() -> ragService.ask(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Search service unavailable");
    }

    @Test
    void ask_WithLlmFailure_PropagatesException() {
        // Given
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(searchResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("LLM service unavailable"));

        // When/Then
        assertThatThrownBy(() -> ragService.ask(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("LLM service unavailable");
    }

    @Test
    void ask_WithThreshold_FiltersLowScoreResults() {
        // Given
        request.setThreshold(0.90);
        
        // Add low-score result that should be filtered out
        UUID lowScoreChunkId = UUID.randomUUID();
        List<SearchResultDto> mixedResults = List.of(
            SearchResultDto.of(UUID.randomUUID(), 0.92, "High score result", UUID.randomUUID(), 1, 4),
            SearchResultDto.of(lowScoreChunkId, 0.80, "Low score result", UUID.randomUUID(), 1, 6)
        );
        
        SearchMetadata mixedMetadata = SearchMetadata.of("test", 2, 2, 100L);
        SemanticSearchResponse mixedResponse = SemanticSearchResponse.of(mixedResults, mixedMetadata);
        
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(mixedResponse);
        
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Answer based on filtered results");

        // When
        AskResponse response = ragService.ask(request);

        // Then
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getCitations().get(0).getScore()).isEqualTo(0.92);
        assertThat(response.getMetadata().getChunksRetrieved()).isEqualTo(2);
        assertThat(response.getMetadata().getChunksUsed()).isEqualTo(1); // Only high-score result used
    }
}
