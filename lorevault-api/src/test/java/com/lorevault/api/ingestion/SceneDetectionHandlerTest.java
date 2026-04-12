package com.lorevault.api.ingestion;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.ai.SceneWithCoordinates;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ai.SceneDetectionService;
import com.lorevault.api.ai.SceneProcessingService;
import com.lorevault.api.timeline.DefaultTemporalEdgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SceneDetectionHandler Tests")
class SceneDetectionHandlerTest {

    @Mock private ChapterGraphRepository chapterRepo;
    @Mock private SceneGraphRepository sceneRepo;
    @Mock private SceneDetectionService sceneDetectionService;
    @Mock private SceneProcessingService sceneProcessingService;
    @Mock private IndividualPersistenceService individualPersistenceService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private DefaultTemporalEdgeService defaultTemporalEdgeService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SceneDetectionHandler handler;

    private UUID jobId;
    private UUID chapterId;
    private UUID bookId;
    private Chapter testChapter;
    private ChapterIngestionEvent testEvent;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        bookId = UUID.randomUUID();

        testChapter = new Chapter();
        BeanWrapperImpl chapterBean = new BeanWrapperImpl(testChapter);
        chapterBean.setPropertyValue("id", chapterId);
        chapterBean.setPropertyValue("bookId", bookId);
        chapterBean.setPropertyValue("rawText", "Test chapter content for scene detection.");

        testEvent = new ChapterIngestionEvent(this, jobId, chapterId);
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should detect scenes and emit ScenesDetectedEvent")
        void handleChapterPersisted_detectsScenesSuccessfully() {
            // Given
            List<SceneWithCoordinates> sceneCoords = List.of(
                    new SceneWithCoordinates(0, 0, 20, "Scene 1"),
                    new SceneWithCoordinates(1, 20, 40, "Scene 2")
            );
            Scene scene1 = createScene(0);
            Scene scene2 = createScene(1);
            List<Scene> persistedScenes = List.of(scene1, scene2);

            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(sceneDetectionService.detectScenesInText(jobId, chapterId, (String) new BeanWrapperImpl(testChapter).getPropertyValue("rawText"))).thenReturn(
                    new SceneDetectionService.SceneDetectionOutcome(sceneCoords, List.of())
            );
            when(sceneProcessingService.persistDetectedScenes(chapterId, sceneCoords)).thenReturn(persistedScenes);

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            verify(sceneDetectionService).detectScenesInText(jobId, chapterId, (String) new BeanWrapperImpl(testChapter).getPropertyValue("rawText"));
            verify(sceneProcessingService).persistDetectedScenes(chapterId, sceneCoords);
            verify(individualPersistenceService).persistExtractedIndividuals(persistedScenes, List.of());
            verify(defaultTemporalEdgeService).createAllDefaults(bookId);

            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            ScenesDetectedEvent emittedEvent = eventCaptor.getValue();
            BeanWrapperImpl eventBean = new BeanWrapperImpl(emittedEvent);
            assertThat(eventBean.getPropertyValue("jobId")).isEqualTo(jobId);
            assertThat(eventBean.getPropertyValue("chapterId")).isEqualTo(chapterId);
            assertThat(eventBean.getPropertyValue("bookId")).isEqualTo(bookId);
            assertThat((List<?>) eventBean.getPropertyValue("sceneIds")).hasSize(2);
        }

        @Test
        @DisplayName("Should emit ScenesDetectedEvent after persisting extracted individuals")
        void handleChapterPersisted_persistsMentionsBeforeEventEmission() {
            List<SceneWithCoordinates> sceneCoords = List.of(new SceneWithCoordinates(0, 0, 20, "Scene 1"));
            Scene scene = createScene(0);
            List<Scene> persistedScenes = List.of(scene);
            List<com.lorevault.api.ai.TriadOrchestrationService.TriadSceneIndividualExtraction> extractions = List.of();

            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(sceneDetectionService.detectScenesInText(jobId, chapterId, (String) new BeanWrapperImpl(testChapter).getPropertyValue("rawText")))
                    .thenReturn(new SceneDetectionService.SceneDetectionOutcome(sceneCoords, extractions));
            when(sceneProcessingService.persistDetectedScenes(chapterId, sceneCoords)).thenReturn(persistedScenes);

            handler.handleChapterIngestion(testEvent);

            verify(individualPersistenceService).persistExtractedIndividuals(persistedScenes, extractions);
            verify(eventPublisher).publishEvent(any(ScenesDetectedEvent.class));
            InOrder inOrder = inOrder(individualPersistenceService, eventPublisher);
            inOrder.verify(individualPersistenceService).persistExtractedIndividuals(persistedScenes, extractions);
            inOrder.verify(eventPublisher).publishEvent(any(ScenesDetectedEvent.class));
        }

        @Test
        @DisplayName("Should skip detection when scenes already exist (idempotency)")
        void handleChapterPersisted_existingScenes_skipDetection() {
            // Given
            List<Scene> existingScenes = List.of(createScene(0), createScene(1));
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(existingScenes);
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            verify(sceneDetectionService, never()).detectScenesInText(any(), any(), anyString());
            verify(sceneProcessingService, never()).persistDetectedScenes(any(), any());
            verify(individualPersistenceService, never()).persistExtractedIndividuals(any(), any());
            
            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat((List<?>) new BeanWrapperImpl(eventCaptor.getValue()).getPropertyValue("sceneIds")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should emit IngestionFailedEvent on LLM error")
        void handleChapterPersisted_llmError_emitsFailure() {
            // Given
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(sceneDetectionService.detectScenesInText(any(), any(), anyString()))
                    .thenThrow(new RuntimeException("LLM API timeout"));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            IngestionFailedEvent failedEvent = eventCaptor.getValue();
            BeanWrapperImpl failedBean = new BeanWrapperImpl(failedEvent);
            assertThat(failedBean.getPropertyValue("jobId")).isEqualTo(jobId);
            assertThat(failedBean.getPropertyValue("failedStage")).isEqualTo("SCENE_DETECTION");
            assertThat(failedBean.getPropertyValue("retryable")).isEqualTo(true); // LLM errors are retryable

            verify(ingestionJobService).updateJobStatus(eq(jobId), eq(IngestionStatus.FAILED), anyString(), any());
        }

        @Test
        @DisplayName("Should handle chapter not found error")
        void handleChapterPersisted_chapterNotFound_emitsFailure() {
            // Given
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.empty());

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            assertThat(new BeanWrapperImpl(eventCaptor.getValue()).getPropertyValue("failedStage")).isEqualTo("SCENE_DETECTION");
        }

        @Test
        @DisplayName("Should emit failure on database error")
        void handleChapterPersisted_databaseError_emitsFailure() {
            // Given
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(sceneRepo.findByChapterId(chapterId))
                    .thenThrow(new RuntimeException("Database error"));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(new BeanWrapperImpl(eventCaptor.getValue()).getPropertyValue("failedStage")).isEqualTo("SCENE_DETECTION");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty chapter text")
        void handleChapterPersisted_emptyText_emitsEventWithZeroScenes() {
            // Given
            new BeanWrapperImpl(testChapter).setPropertyValue("rawText", "");
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            verify(sceneDetectionService, never()).detectScenesInText(any(), any(), anyString());
            verify(individualPersistenceService, never()).persistExtractedIndividuals(any(), any());
            
            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat((List<?>) new BeanWrapperImpl(eventCaptor.getValue()).getPropertyValue("sceneIds")).isEmpty();
        }

        @Test
        @DisplayName("Should handle null chapter text")
        void handleChapterPersisted_nullText_emitsEventWithZeroScenes() {
            // Given
            new BeanWrapperImpl(testChapter).setPropertyValue("rawText", null);
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat((List<?>) new BeanWrapperImpl(eventCaptor.getValue()).getPropertyValue("sceneIds")).isEmpty();
        }
    }

    private Scene createScene(int index) {
        return new Scene(UUID.randomUUID(), index, 0L, 1L, "ctx", "text", chapterId, null, null, null, null, null);
    }
}
