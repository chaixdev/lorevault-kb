package com.lorevault.api.search;

import com.lorevault.api.content.Chunk;
import com.lorevault.api.search.AskDtos;
import com.lorevault.api.search.SemanticSearchDtos;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.ai.PromptRepository;
import com.lorevault.api.search.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class RagServiceTest {

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private PromptRepository mockPromptRepository;

    @Mock
    private ChunkGraphRepository chunkRepo;

    @Mock
    private ChapterGraphRepository chapterRepo;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.ChatClientRequestSpec systemSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(semanticSearchService, mockPromptRepository, chunkRepo, chapterRepo, chatClient);
        
        ReflectionTestUtils.setField(ragService, "modelId", "test-model");
    }

    @Nested
    class AskMethod {

        @Test
        void shouldIncludeNestedCoordinatesInCitations() {
            // Arrange
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);

            AskDtos.AskRequest request = new AskDtos.AskRequest();
            request.setQuestion("Who is Kaladin?");
            request.setTopK(3);

            UUID chunkId = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();

            SemanticSearchDtos.SearchResultDto searchResult = SemanticSearchDtos.SearchResultDto.of(
                chunkId, 0.93, "Kaladin is a spearman.", chapterId, 1, 1
            );
            SemanticSearchDtos.SemanticSearchResponse searchResponse = new SemanticSearchDtos.SemanticSearchResponse();
            searchResponse.setResults(List.of(searchResult));

            // Mock chunk
            Chunk mockChunk = new Chunk();
            mockChunk.setId(chunkId);
            mockChunk.setText("Kaladin is a spearman.");
            when(chunkRepo.findById(chunkId))
                .thenReturn(Optional.of(mockChunk));

            com.lorevault.api.content.Chapter chapter = new com.lorevault.api.content.Chapter();
            chapter.setId(chapterId);
            chapter.setUniverse("Cosmere");
            chapter.setSeries("Stormlight Archive");
            chapter.setBookTitle("The Way of Kings");
            chapter.setChapterTitle("Kaladin");
            chapter.setBookNumber(1);
            chapter.setChapterNumber(1);
            when(chapterRepo.findById(chapterId))
                .thenReturn(Optional.of(chapter));

            when(semanticSearchService.search(any(SemanticSearchDtos.SemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new org.springframework.ai.chat.prompt.PromptTemplate("You are a helpful assistant."));
            when(callSpec.content())
                .thenReturn("Kaladin is a spearman. [1]");

            // Act
            AskDtos.AskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCitations()).hasSize(1);
            AskDtos.CitationDto citation = response.getCitations().get(0);
            assertThat(citation.getCoordinates()).isNotNull();
            assertThat(citation.getCoordinates().getUniverse()).isEqualTo("Cosmere");
            assertThat(citation.getCoordinates().getSeries()).isEqualTo("Stormlight Archive");
            assertThat(citation.getCoordinates().getBookTitle()).isEqualTo("The Way of Kings");
            assertThat(citation.getCoordinates().getChapterTitle()).isEqualTo("Kaladin");
            assertThat(citation.getCoordinates().getBookNumber()).isEqualTo(1);
            assertThat(citation.getCoordinates().getChapterNumber()).isEqualTo(1);
        }

        @Test
        void shouldGenerateAnswerWithCitations() {
            // Arrange
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);

            AskDtos.AskRequest request = new AskDtos.AskRequest();
            request.setQuestion("What is the meaning of life?");
            request.setTopK(5);
            request.setThreshold(0.8);

            UUID chunkId = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();
            
            SemanticSearchDtos.SearchResultDto searchResult = SemanticSearchDtos.SearchResultDto.of(
                chunkId, 0.95, "The meaning of life is 42.", chapterId, 1, 1
            );
            List<SemanticSearchDtos.SearchResultDto> searchResults = List.of(searchResult);

            SemanticSearchDtos.SemanticSearchResponse searchResponse = new SemanticSearchDtos.SemanticSearchResponse();
            searchResponse.setResults(searchResults);

            String llmResponse = "Based on the context, the meaning of life is 42. [1]";

            // Mock chunk for context building
            Chunk mockChunk = new Chunk();
            mockChunk.setId(chunkId);
            mockChunk.setText("The meaning of life is 42.");
            when(chunkRepo.findById(chunkId))
                .thenReturn(Optional.of(mockChunk));

            // Mock chapter for citation building (return empty for simplicity)
            when(chapterRepo.findById(chapterId))
                .thenReturn(Optional.empty());

            when(semanticSearchService.search(any(SemanticSearchDtos.SemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new PromptTemplate("You are a helpful assistant."));
            when(callSpec.content())
                .thenReturn(llmResponse);

            // Act
            AskDtos.AskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAnswer()).isEqualTo(llmResponse);
            assertThat(response.getCitations()).hasSize(1);
            assertThat(response.getCitations().get(0).getChunkId()).isEqualTo(chunkId);
            assertThat(response.getCitations().get(0).getSnippet()).isEqualTo("The meaning of life is 42.");
            assertThat(response.getCitations().get(0).getScore()).isEqualTo(0.95);
            // Check coordinates (fallback case - chapter not found, so minimal coordinates)
            assertThat(response.getCitations().get(0).getCoordinates()).isNotNull();
            assertThat(response.getCitations().get(0).getCoordinates().getBookNumber()).isEqualTo(1);
            assertThat(response.getCitations().get(0).getCoordinates().getChapterNumber()).isEqualTo(1);

            assertThat(response.getMetadata()).isNotNull();
            assertThat(response.getMetadata().getQuestion()).isEqualTo("What is the meaning of life?");
            assertThat(response.getMetadata().getChunksRetrieved()).isEqualTo(1);
            assertThat(response.getMetadata().getChunksUsed()).isEqualTo(1);
            assertThat(response.getMetadata().getModelId()).isEqualTo("test-model");

            verify(semanticSearchService).search(any(SemanticSearchDtos.SemanticSearchRequest.class));
            verify(chunkRepo).findById(chunkId);
            verify(chapterRepo).findById(chapterId);
            verify(callSpec).content();
        }

        @Test
        void shouldHandleNoSearchResults() {
            // Arrange
            AskDtos.AskRequest request = new AskDtos.AskRequest();
            request.setQuestion("What is the meaning of life?");
            request.setTopK(5);

            SemanticSearchDtos.SemanticSearchResponse searchResponse = new SemanticSearchDtos.SemanticSearchResponse();
            searchResponse.setResults(Collections.emptyList());

            when(semanticSearchService.search(any(SemanticSearchDtos.SemanticSearchRequest.class)))
                .thenReturn(searchResponse);

            // Act
            AskDtos.AskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAnswer()).isEqualTo("No evidence found in the knowledge base to answer this question.");
            assertThat(response.getCitations()).isEmpty();
            assertThat(response.getMetadata().getChunksRetrieved()).isEqualTo(0);
            assertThat(response.getMetadata().getChunksUsed()).isEqualTo(0);

            verify(semanticSearchService).search(any(SemanticSearchDtos.SemanticSearchRequest.class));
            verify(chatClient, never()).prompt();
        }

        @Test
        void shouldFilterResultsByThreshold() {
            // Arrange
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);

            AskDtos.AskRequest request = new AskDtos.AskRequest();
            request.setQuestion("What is AI?");
            request.setTopK(5);
            request.setThreshold(0.9);

            UUID chunkId1 = UUID.randomUUID();
            UUID chunkId2 = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();
            
            SemanticSearchDtos.SearchResultDto highScoreResult = SemanticSearchDtos.SearchResultDto.of(
                chunkId1, 0.95, "AI is artificial intelligence.", chapterId, 1, 1
            );
            SemanticSearchDtos.SearchResultDto lowScoreResult = SemanticSearchDtos.SearchResultDto.of(
                chunkId2, 0.85, "Machine learning is a subset of AI.", chapterId, 1, 2
            );
            
            List<SemanticSearchDtos.SearchResultDto> searchResults = List.of(highScoreResult, lowScoreResult);

            SemanticSearchDtos.SemanticSearchResponse searchResponse = new SemanticSearchDtos.SemanticSearchResponse();
            searchResponse.setResults(searchResults);

            String llmResponse = "AI stands for artificial intelligence. [1]";

            // Mock chunk for context building (only the high-score chunk will be used)
            Chunk mockChunk1 = new Chunk();
            mockChunk1.setId(chunkId1);
            mockChunk1.setText("AI is artificial intelligence.");
            when(chunkRepo.findById(chunkId1))
                .thenReturn(Optional.of(mockChunk1));

            // Mock chapter for citation building (return empty for simplicity)
            when(chapterRepo.findById(chapterId))
                .thenReturn(Optional.empty());

            when(semanticSearchService.search(any(SemanticSearchDtos.SemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new PromptTemplate("You are a helpful assistant."));
            when(callSpec.content())
                .thenReturn(llmResponse);

            // Act
            AskDtos.AskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAnswer()).isEqualTo(llmResponse);
            assertThat(response.getCitations()).hasSize(1);
            assertThat(response.getCitations().get(0).getChunkId()).isEqualTo(chunkId1);
            assertThat(response.getCitations().get(0).getScore()).isEqualTo(0.95);
            
            assertThat(response.getMetadata().getChunksRetrieved()).isEqualTo(2);
            assertThat(response.getMetadata().getChunksUsed()).isEqualTo(1);

            verify(semanticSearchService).search(any(SemanticSearchDtos.SemanticSearchRequest.class));
            verify(chunkRepo).findById(chunkId1);
            verify(chapterRepo).findById(chapterId);
            verify(callSpec).content();
        }
    }
}
