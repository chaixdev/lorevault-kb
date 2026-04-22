package com.lorevault.api.search;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.content.Chunk;
import com.lorevault.api.search.application.CoreSearchRecords.*;

import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.ai.PromptRepository;
import com.lorevault.api.search.application.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private QuestionIntentClassifier intentClassifier;

    @Mock
    private CypherTemplateRegistry templateRegistry;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(
                semanticSearchService, mockPromptRepository, chunkRepo, chapterRepo,
                intentClassifier, templateRegistry, chatClient);

        ReflectionTestUtils.setField(ragService, "modelId", "test-model");
    }

    // Helper: build a SemanticSearchResponse using the static factory
    private static CoreSearchRecords.CoreSemanticSearchResponse searchResponseOf(
            List<CoreSearchRecords.CoreSearchResult> results) {
        return new CoreSearchRecords.CoreSemanticSearchResponse(
                results,
                new CoreSearchRecords.CoreSearchMetadata("", results.size(), results.size(), 0L));
    }

    @Nested
    class AskMethod {

        @Test
        void shouldIncludeNestedCoordinatesInCitations() {
            // Arrange — question is narrative QA (not entity lookup)
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.NARRATIVE_QA);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);

            CoreAskRequest request = new CoreAskRequest("What does Kaladin do?", 3, null, null, null);

            UUID chunkId = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();

            CoreSearchResult searchResult = new CoreSearchResult(chunkId, 0.93, "Kaladin is a spearman.", chapterId, 1, 1,
                    UUID.randomUUID(), "Scene summary", List.of(), List.of());
            CoreSemanticSearchResponse searchResponse = searchResponseOf(List.of(searchResult));

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

            when(semanticSearchService.search(any(CoreSemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new org.springframework.ai.chat.prompt.PromptTemplate("You are a helpful assistant."));
            when(callSpec.content())
                .thenReturn("Kaladin is a spearman. [1]");

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.citations()).hasSize(1);
            CoreCitation citation = response.citations().get(0);
            assertThat(citation.coordinates()).isNotNull();
            assertThat(citation.coordinates().getUniverse()).isEqualTo("Cosmere");
            assertThat(citation.coordinates().getSeries()).isEqualTo("Stormlight Archive");
            assertThat(citation.coordinates().getBookTitle()).isEqualTo("The Way of Kings");
            assertThat(citation.coordinates().getChapterTitle()).isEqualTo("Kaladin");
            assertThat(citation.coordinates().getBookNumber()).isEqualTo(1);
            assertThat(citation.coordinates().getChapterNumber()).isEqualTo(1);
        }

        @Test
        void shouldGenerateAnswerWithCitations() {
            // Arrange
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.NARRATIVE_QA);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec, null, null, null, null);

            CoreAskRequest request = new CoreAskRequest("What is the meaning of life?", 5, 0.8, null, null);

            UUID chunkId = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();
            
            CoreSearchResult searchResult = new CoreSearchResult(chunkId, 0.95, "The meaning of life is 42.", chapterId, 1, 1,
                    UUID.randomUUID(), "Scene summary", List.of(), List.of());
            CoreSemanticSearchResponse searchResponse = searchResponseOf(List.of(searchResult));

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

            when(semanticSearchService.search(any(CoreSemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new PromptTemplate("You are a helpful assistant."));
            when(callSpec.content())
                .thenReturn(llmResponse);

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.answer()).isEqualTo(llmResponse);
            assertThat(response.citations()).hasSize(1);
            assertThat(response.citations().get(0).chunkId()).isEqualTo(chunkId);
            assertThat(response.citations().get(0).snippet()).isEqualTo("The meaning of life is 42.");
            assertThat(response.citations().get(0).score()).isEqualTo(0.95);
            // Check coordinates (fallback case - chapter not found, so minimal coordinates)
            assertThat(response.citations().get(0).coordinates()).isNotNull();
            assertThat(response.citations().get(0).coordinates().getBookNumber()).isEqualTo(1);
            assertThat(response.citations().get(0).coordinates().getChapterNumber()).isEqualTo(1);

            assertThat(response.metadata()).isNotNull();
            assertThat(response.metadata().question()).isEqualTo("What is the meaning of life?");
            assertThat(response.metadata().chunksRetrieved()).isEqualTo(1);
            assertThat(response.metadata().chunksUsed()).isEqualTo(1);
            assertThat(response.metadata().modelId()).isEqualTo("test-model");

            verify(semanticSearchService).search(any(CoreSemanticSearchRequest.class));
            verify(chunkRepo).findById(chunkId);
            verify(chapterRepo).findById(chapterId);
            verify(callSpec).content();
        }

        @Test
        void shouldHandleNoSearchResults() {
            // Arrange
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.NARRATIVE_QA);

            CoreAskRequest request = new CoreAskRequest("What is the meaning of life?", 5, 0.8, null, null);

            CoreSemanticSearchResponse searchResponse = searchResponseOf(Collections.emptyList());

            when(semanticSearchService.search(any(CoreSemanticSearchRequest.class)))
                .thenReturn(searchResponse);

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.answer()).isEqualTo("No evidence found in the knowledge base to answer this question.");
            assertThat(response.citations()).isEmpty();
            assertThat(response.metadata().chunksRetrieved()).isEqualTo(0);
            assertThat(response.metadata().chunksUsed()).isEqualTo(0);

            verify(semanticSearchService).search(any(CoreSemanticSearchRequest.class));
            verify(chatClient, never()).prompt();
        }

        @Test
        void shouldFilterResultsByThreshold() {
            // Arrange
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.NARRATIVE_QA);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);

            CoreAskRequest request = new CoreAskRequest("What is AI?", 5, 0.9, null, null);

            UUID chunkId1 = UUID.randomUUID();
            UUID chunkId2 = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();
            
            CoreSearchResult highScoreResult = new CoreSearchResult(chunkId1, 0.95, "AI is artificial intelligence.", chapterId, 1, 1
            , null, null, null, null);
            CoreSearchResult lowScoreResult = new CoreSearchResult(chunkId2, 0.85, "Machine learning is a subset of AI.", chapterId, 1, 2
            , null, null, null, null);
            
            CoreSemanticSearchResponse searchResponse =
                    searchResponseOf(List.of(highScoreResult, lowScoreResult));

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

            when(semanticSearchService.search(any(CoreSemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new PromptTemplate("You are a helpful assistant."));
            when(callSpec.content())
                .thenReturn(llmResponse);

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.answer()).isEqualTo(llmResponse);
            assertThat(response.citations()).hasSize(1);
            assertThat(response.citations().get(0).chunkId()).isEqualTo(chunkId1);
            assertThat(response.citations().get(0).score()).isEqualTo(0.95);
            
            assertThat(response.metadata().chunksRetrieved()).isEqualTo(2);
            assertThat(response.metadata().chunksUsed()).isEqualTo(1);

            verify(semanticSearchService).search(any(CoreSemanticSearchRequest.class));
            verify(chunkRepo).findById(chunkId1);
            verify(chapterRepo).findById(chapterId);
            verify(callSpec).content();
        }

        @Test
        void shouldAppendEntityAndLocationAnnotationsToContextWhenPresent() {
            // Arrange — narrative QA path (entity expansion already in vector results)
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.NARRATIVE_QA);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(anyString())).thenReturn(systemSpec);
            ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
            when(systemSpec.user(userPromptCaptor.capture())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("Vin is the main protagonist.");

            CoreAskRequest request = new CoreAskRequest("What does Vin do in the mists?", 3, null, null, null);

            UUID chunkId  = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();

            // Result carrying scene-level entity data
            CoreSearchResult searchResult = new CoreSearchResult(chunkId, 0.91, "Vin crept through the mists.", chapterId, 2, 5, UUID.randomUUID(), "Vin infiltrates the keep", List.of("Vin", "Kelsier"),
                List.of("Luthadel")
            );

            CoreSemanticSearchResponse searchResponse = searchResponseOf(List.of(searchResult));

            Chunk mockChunk = new Chunk();
            mockChunk.setId(chunkId);
            mockChunk.setText("Vin crept through the mists.");
            when(chunkRepo.findById(chunkId)).thenReturn(Optional.of(mockChunk));
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.empty());

            when(semanticSearchService.search(any(CoreSemanticSearchRequest.class)))
                .thenReturn(searchResponse);
            when(mockPromptRepository.get("rag-answer-generation"))
                .thenReturn(new PromptTemplate("You are a helpful assistant."));

            // Act
            ragService.ask(request);

            // Assert — the user prompt sent to the LLM must contain both annotations
            String capturedUserPrompt = userPromptCaptor.getValue();
            assertThat(capturedUserPrompt).contains("featuring: Vin, Kelsier");
            assertThat(capturedUserPrompt).contains("at: Luthadel");
            assertThat(capturedUserPrompt).contains("Book 2, Chapter 5");
        }

        @Test
        void shouldRouteEntityLookupToTemplateRegistryAndFallBackWhenEmpty() {
            // Arrange — entity lookup intent, but template returns nothing → falls back to narrative QA
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.ENTITY_LOOKUP);
            when(templateRegistry.execute(any(), any(), any())).thenReturn(List.of());

            // Narrative QA fallback path
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.ENTITY_LOOKUP);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("Vin is a Mistborn.");

            when(mockPromptRepository.get("rag-answer-generation"))
                    .thenReturn(new PromptTemplate("You are a helpful assistant."));

            CoreAskRequest request = new CoreAskRequest("Who is Vin?", 3, null, null, null);

            CoreSearchResult fallbackResult = new CoreSearchResult(UUID.randomUUID(), 0.8, "Fallback text", UUID.randomUUID(), 1, 1, UUID.randomUUID(), "Series", List.of(), List.of());
            CoreSearchMetadata metadata = new CoreSearchMetadata("Who is Vin?", 1, 1, 10L);
            CoreSemanticSearchResponse fallbackResponse = new CoreSemanticSearchResponse(List.of(fallbackResult), metadata);
            when(semanticSearchService.search(any())).thenReturn(fallbackResponse);

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert — fell back to narrative QA, got an answer
            assertThat(response.answer()).isEqualTo("Vin is a Mistborn.");
        }

        @Test
        void shouldReturnEntityAnswerWhenTemplateRegistryHasResults() {
            // Arrange — entity lookup intent, template returns a result
            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.ENTITY_LOOKUP);

            CypherTemplateRegistry.EntityLookupResult entityResult =
                    new CypherTemplateRegistry.EntityLookupResult(
                            "individual-lookup", "Vin", "vin", 47,
                            "chapter-uuid-1", 1, 3, null, null, null, null);

            when(templateRegistry.execute(any(), any(), any())).thenReturn(List.of(entityResult));

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("Vin is a Mistborn who appears in 47 chapters.");

            when(mockPromptRepository.get("rag-answer-generation"))
                    .thenReturn(new PromptTemplate("You are a helpful assistant."));

            CoreAskRequest request = new CoreAskRequest("Who is Vin?", 3, null, null, null);

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert — entity lane answered, no semantic search called
            assertThat(response.answer()).isEqualTo("Vin is a Mistborn who appears in 47 chapters.");
            verify(semanticSearchService, never()).search(any());
        }

        @Test
        void shouldUseHybridRrfFusionForNarrativeQaWhenEnabled() {
            // Arrange
            ReflectionTestUtils.setField(ragService, "hybridEnabled", true);
            ReflectionTestUtils.setField(ragService, "hybridRagOnly", true);
            ReflectionTestUtils.setField(ragService, "hybridBranchN", 20);
            ReflectionTestUtils.setField(ragService, "hybridRrfK", 60);

            when(intentClassifier.classify(any())).thenReturn(QuestionIntent.NARRATIVE_QA);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(systemSpec);
            when(systemSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("Vin appears in both graph and vector evidence.");
            when(mockPromptRepository.get("rag-answer-generation"))
                    .thenReturn(new PromptTemplate("You are a helpful assistant."));

            CoreAskRequest request = new CoreAskRequest("Who is Vin?", 2, null, null, null);

            UUID sharedChunkId = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();
            UUID sceneId = UUID.randomUUID();

            CoreSearchResult vectorResult = new CoreSearchResult(sharedChunkId, 0.91, "Vector evidence", chapterId, 1, 1, sceneId, "Vector scene summary", List.of("Vin"), List.of("Luthadel")
            );

            when(semanticSearchService.search(any(CoreSemanticSearchRequest.class)))
                    .thenReturn(searchResponseOf(List.of(vectorResult)));

            CypherTemplateRegistry.EntityLookupResult graphScene =
                    new CypherTemplateRegistry.EntityLookupResult(
                            "individual-scenes",
                            "Vin",
                            "vin",
                            null,
                            null,
                            1,
                            1,
                            sceneId.toString(),
                            "Graph scene summary",
                            1,
                            1);
            when(templateRegistry.execute(any(), any(), any())).thenReturn(List.of(graphScene));

            Chunk graphChunk = new Chunk();
            graphChunk.setId(sharedChunkId);
            graphChunk.setText("Graph evidence");
            when(chunkRepo.findBySceneId(sceneId)).thenReturn(List.of(graphChunk));
            when(chunkRepo.findById(sharedChunkId)).thenReturn(Optional.of(graphChunk));
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.empty());

            // Act
            CoreAskResponse response = ragService.ask(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.answer()).isEqualTo("Vin appears in both graph and vector evidence.");
            assertThat(response.citations()).hasSize(1); // deduped by chunkId
            assertThat(response.citations().get(0).chunkId()).isEqualTo(sharedChunkId);
            assertThat(response.metadata().chunksRetrieved()).isEqualTo(1);
            assertThat(response.metadata().chunksUsed()).isEqualTo(1);
        }
    }
}
