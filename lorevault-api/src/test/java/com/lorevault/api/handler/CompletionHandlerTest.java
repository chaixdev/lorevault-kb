package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.event.ingestion.EmbeddingsGeneratedEvent;
import com.lorevault.api.event.ingestion.IngestionCompletedEvent;
import com.lorevault.api.service.ingestion.IngestionJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompletionHandler Tests")
class CompletionHandlerTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CompletionHandler handler;

    private UUID jobId;
    private UUID chapterId;
    private UUID bookId;
    private Chapter testChapter;
    private IngestionJob testJob;
    private EmbeddingsGeneratedEvent testEvent;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        bookId = UUID.randomUUID();

        testChapter = new Chapter();
        testChapter.setId(chapterId);
        testChapter.setBookId(bookId);
        testChapter.setRawText("Test chapter content with some text for measurement.");

        testJob = new IngestionJob();
        testJob.setId(jobId);
        testJob.setChapterId(chapterId);

        testEvent = new EmbeddingsGeneratedEvent(this, jobId, chapterId, bookId, 10);
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should complete job and emit IngestionCompletedEvent")
        void handleEmbeddingsGenerated_completesJobSuccessfully() {
            // Given
            List<Scene> scenes = List.of(createScene(), createScene(), createScene());
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(scenes);
            when(contentPersistencePort.countChunksByChapterId(chapterId)).thenReturn(15);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findJob(jobId)).thenReturn(Optional.of(testJob));

            // When
            handler.handleEmbeddingsGenerated(testEvent);

            // Then
            verify(ingestionJobService).completeJob(eq(testJob), eq(chapterId), anyInt());

            ArgumentCaptor<IngestionCompletedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            IngestionCompletedEvent emittedEvent = eventCaptor.getValue();
            assertThat(emittedEvent.getJobId()).isEqualTo(jobId);
            assertThat(emittedEvent.getChapterId()).isEqualTo(chapterId);
            assertThat(emittedEvent.getTotalScenes()).isEqualTo(3);
            assertThat(emittedEvent.getTotalChunks()).isEqualTo(15);
            assertThat(emittedEvent.getTotalEmbeddings()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should gather statistics before completion")
        void handleEmbeddingsGenerated_gathersStatistics() {
            // Given
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(List.of(createScene()));
            when(contentPersistencePort.countChunksByChapterId(chapterId)).thenReturn(5);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findJob(jobId)).thenReturn(Optional.of(testJob));

            // When
            handler.handleEmbeddingsGenerated(testEvent);

            // Then
            verify(contentPersistencePort).findScenesByChapterId(chapterId);
            verify(contentPersistencePort).countChunksByChapterId(chapterId);
            verify(contentPersistencePort).findChapterById(chapterId);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle chapter not found without emitting failure")
        void handleEmbeddingsGenerated_chapterNotFound_noFailureEvent() {
            // Given - work is done, just completion tracking fails
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(List.of());
            when(contentPersistencePort.countChunksByChapterId(chapterId)).thenReturn(0);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.empty());

            // When
            handler.handleEmbeddingsGenerated(testEvent);

            // Then - no failure event emitted, work was already done
            verify(eventPublisher, never()).publishEvent(any(IngestionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should handle job not found without emitting failure")
        void handleEmbeddingsGenerated_jobNotFound_noFailureEvent() {
            // Given
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(List.of());
            when(contentPersistencePort.countChunksByChapterId(chapterId)).thenReturn(0);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findJob(jobId)).thenReturn(Optional.empty());

            // When
            handler.handleEmbeddingsGenerated(testEvent);

            // Then
            verify(eventPublisher, never()).publishEvent(any(IngestionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should handle database errors gracefully")
        void handleEmbeddingsGenerated_databaseError_noException() {
            // Given
            when(contentPersistencePort.findScenesByChapterId(chapterId))
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            handler.handleEmbeddingsGenerated(testEvent);

            // Then
            verify(eventPublisher, never()).publishEvent(any(IngestionCompletedEvent.class));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null chapter text")
        void handleEmbeddingsGenerated_nullChapterText_usesZeroLength() {
            // Given
            testChapter.setRawText(null);
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(List.of());
            when(contentPersistencePort.countChunksByChapterId(chapterId)).thenReturn(0);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findJob(jobId)).thenReturn(Optional.of(testJob));

            // When
            handler.handleEmbeddingsGenerated(testEvent);

            // Then
            verify(ingestionJobService).completeJob(testJob, chapterId, 0);
        }
    }

    private Scene createScene() {
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        return scene;
    }
}
