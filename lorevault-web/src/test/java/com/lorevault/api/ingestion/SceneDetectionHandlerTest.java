package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
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

import com.lorevault.api.ai.domain.SceneLocalizationException;
import com.lorevault.api.ai.domain.SceneWithCoordinates;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Scene;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ai.application.SceneDetectionService;
import com.lorevault.api.ai.application.SceneProcessingService;
import com.lorevault.api.timeline.application.DefaultTemporalEdgeService;
import com.lorevault.api.timeline.application.TriadEdgePersistenceService;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
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
    @Mock private LocationPersistenceService locationPersistenceService;
    @Mock private EventPersistenceService eventPersistenceService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private DefaultTemporalEdgeService defaultTemporalEdgeService;
    @Mock private TriadEdgePersistenceService triadEdgePersistenceService;
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
        testChapter.setId(chapterId);
        testChapter.setBookId(bookId);
        testChapter.setRawText("Test chapter content for scene detection.");

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
            when(sceneDetectionService.detectScenesInChapter(jobId, testChapter)).thenReturn(
                    new SceneDetectionService.SceneDetectionOutcome(sceneCoords, List.of(), List.of(), List.of())
            );
            when(sceneProcessingService.persistDetectedScenes(chapterId, sceneCoords)).thenReturn(persistedScenes);

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            verify(sceneDetectionService).detectScenesInChapter(jobId, testChapter);
            verify(sceneProcessingService).persistDetectedScenes(chapterId, sceneCoords);
            verify(individualPersistenceService).persistExtractedIndividuals(persistedScenes, List.of());
            verify(locationPersistenceService).persistExtractedLocations(persistedScenes, List.of());
            verify(eventPersistenceService).persistExtractedEvents(persistedScenes, List.of());
            verify(defaultTemporalEdgeService).createAllDefaults(bookId);
            verify(triadEdgePersistenceService).applyTriadAnalysesPostPersistence(eq(chapterId), eq(List.of()), anyMap());

            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            ScenesDetectedEvent emittedEvent = eventCaptor.getValue();
            assertThat(emittedEvent.getJobId()).isEqualTo(jobId);
            assertThat(emittedEvent.getChapterId()).isEqualTo(chapterId);
            assertThat(emittedEvent.getBookId()).isEqualTo(bookId);
            assertThat(emittedEvent.getSceneIds()).hasSize(2);
        }

        @Test
        @DisplayName("Should emit ScenesDetectedEvent after persisting extracted individuals")
        void handleChapterPersisted_persistsMentionsBeforeEventEmission() {
            List<SceneWithCoordinates> sceneCoords = List.of(new SceneWithCoordinates(0, 0, 20, "Scene 1"));
            Scene scene = createScene(0);
            List<Scene> persistedScenes = List.of(scene);
            List<com.lorevault.api.ai.application.TriadOrchestrationService.TriadSceneIndividualExtraction> extractions = List.of();
            List<com.lorevault.api.ai.application.TriadOrchestrationService.TriadSceneLocationExtraction> locationExtractions = List.of();
            List<com.lorevault.api.ai.application.TriadOrchestrationService.TriadSceneEventExtraction> eventExtractions = List.of(
                    new com.lorevault.api.ai.application.TriadOrchestrationService.TriadSceneEventExtraction(
                            0,
                            List.of(new com.lorevault.api.ai.application.TriadOrchestrationService.TriadEventExtraction(
                                    "The Winter War",
                                    "war",
                                    "R:temporal.before",
                                    "Explicit",
                                    "They still speak of the Winter War"
                            ))
                    )
            );

            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(sceneDetectionService.detectScenesInChapter(jobId, testChapter))
                    .thenReturn(new SceneDetectionService.SceneDetectionOutcome(sceneCoords, List.of(), extractions, locationExtractions, eventExtractions));
            when(sceneProcessingService.persistDetectedScenes(chapterId, sceneCoords)).thenReturn(persistedScenes);

            handler.handleChapterIngestion(testEvent);

            verify(individualPersistenceService).persistExtractedIndividuals(persistedScenes, extractions);
            verify(locationPersistenceService).persistExtractedLocations(persistedScenes, locationExtractions);
            verify(eventPersistenceService).persistExtractedEvents(persistedScenes, eventExtractions);
            verify(triadEdgePersistenceService).applyTriadAnalysesPostPersistence(eq(chapterId), eq(List.of()), anyMap());
            verify(eventPublisher).publishEvent(any(ScenesDetectedEvent.class));
            InOrder inOrder = inOrder(individualPersistenceService, locationPersistenceService, eventPersistenceService, eventPublisher);
            inOrder.verify(individualPersistenceService).persistExtractedIndividuals(persistedScenes, extractions);
            inOrder.verify(locationPersistenceService).persistExtractedLocations(persistedScenes, locationExtractions);
            inOrder.verify(eventPersistenceService).persistExtractedEvents(persistedScenes, eventExtractions);
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
            verify(sceneDetectionService, never()).detectScenesInChapter(any(), any());
            verify(sceneProcessingService, never()).persistDetectedScenes(any(), any());
            verify(individualPersistenceService, never()).persistExtractedIndividuals(any(), any());
            verify(locationPersistenceService, never()).persistExtractedLocations(any(), any());
            verify(eventPersistenceService, never()).persistExtractedEvents(any(), any());
            verify(triadEdgePersistenceService, never()).applyTriadAnalysesPostPersistence(any(), any(), anyMap());
            verify(defaultTemporalEdgeService, never()).createAllDefaults(any());
            
            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getSceneIds()).hasSize(2);
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
            when(sceneDetectionService.detectScenesInChapter(any(), any()))
                    .thenThrow(new RuntimeException("LLM API timeout"));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            IngestionFailedEvent failedEvent = eventCaptor.getValue();
            assertThat(failedEvent.getJobId()).isEqualTo(jobId);
            assertThat(failedEvent.getFailedStage()).isEqualTo("SCENE_DETECTION");
            assertThat(failedEvent.isRetryable()).isEqualTo(true); // LLM errors are retryable

            verify(ingestionJobService).updateJobStatus(eq(jobId), eq(IngestionStatus.FAILED), anyString(), any());
            verify(eventPublisher, never()).publishEvent(any(ScenesDetectedEvent.class));
        }

        @Test
        @DisplayName("Should mark scene localization mismatch as retryable handled failure")
        void handleChapterPersisted_sceneLocalizationMismatch_emitsRetryableFailure() {
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(sceneDetectionService.detectScenesInChapter(any(), any()))
                    .thenThrow(new SceneLocalizationException(
                            IngestionFailure.builder(
                                            "SCENE_LOCALIZATION_ANCHOR_NOT_FOUND",
                                            "Failed to localize scene 4 because start anchor 'anchor' was not found"
                                    )
                                    .exceptionType(SceneLocalizationException.class.getSimpleName())
                                    .stage("SCENE_SEGMENTATION")
                                    .detail("sceneIndex", 4)
                                    .detail("startAnchor", "anchor")
                                    .build()
                    ));

            handler.handleChapterIngestion(testEvent);

            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            IngestionFailedEvent failedEvent = eventCaptor.getValue();
            assertThat(failedEvent.getJobId()).isEqualTo(jobId);
            assertThat(failedEvent.getFailedStage()).isEqualTo("SCENE_DETECTION");
            assertThat(failedEvent.isRetryable()).isTrue();
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
            
            assertThat(eventCaptor.getValue().getFailedStage()).isEqualTo("SCENE_DETECTION");
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
            assertThat(eventCaptor.getValue().getFailedStage()).isEqualTo("SCENE_DETECTION");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty chapter text")
        void handleChapterPersisted_emptyText_emitsEventWithZeroScenes() {
            // Given
            testChapter.setRawText("");
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            verify(sceneDetectionService, never()).detectScenesInChapter(any(), any());
            verify(individualPersistenceService, never()).persistExtractedIndividuals(any(), any());
            verify(eventPersistenceService, never()).persistExtractedEvents(any(), any());
            
            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getSceneIds()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null chapter text")
        void handleChapterPersisted_nullText_emitsEventWithZeroScenes() {
            // Given
            testChapter.setRawText(null);
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<ScenesDetectedEvent> eventCaptor = ArgumentCaptor.forClass(ScenesDetectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getSceneIds()).isEmpty();
        }
    }

    private Scene createScene(int index) {
        return new Scene(UUID.randomUUID(), index, 0L, 1L, "ctx", "text", chapterId, null, null, null, null, null);
    }
}
