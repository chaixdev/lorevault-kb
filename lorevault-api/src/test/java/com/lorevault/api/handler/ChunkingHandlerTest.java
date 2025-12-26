package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.event.ingestion.ChunksCreatedEvent;
import com.lorevault.api.event.ingestion.IngestionFailedEvent;
import com.lorevault.api.event.ingestion.ScenesDetectedEvent;
import com.lorevault.api.service.content.TextChunkingService;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkingHandler Tests")
class ChunkingHandlerTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private TextChunkingService textChunkingService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChunkingHandler handler;

    private UUID jobId;
    private UUID chapterId;
    private UUID bookId;
    private UUID sceneId1;
    private UUID sceneId2;
    private Chapter testChapter;
    private ScenesDetectedEvent testEvent;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        sceneId1 = UUID.randomUUID();
        sceneId2 = UUID.randomUUID();

        testChapter = new Chapter();
        testChapter.setId(chapterId);
        testChapter.setBookId(bookId);
        testChapter.setRawText("First scene text content here. Second scene text content follows.");

        testEvent = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(sceneId1, sceneId2));
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should create chunks and emit ChunksCreatedEvent")
        void handleScenesDetected_createsChunksSuccessfully() {
            // Given
            Scene scene1 = createScene(sceneId1, 0, 0L, 32L);
            Scene scene2 = createScene(sceneId2, 1, 32L, 65L);
            List<Scene> scenes = List.of(scene1, scene2);
            List<Chunk> chunks = createChunks(2);

            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(scenes);
            when(textChunkingService.extractChunks(anyString())).thenReturn(chunks);

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            verify(textChunkingService, times(2)).extractChunks(anyString());
            verify(contentPersistencePort, times(2)).addChunksToScene(any(), any());

            ArgumentCaptor<ChunksCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ChunksCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            ChunksCreatedEvent emittedEvent = eventCaptor.getValue();
            assertThat(emittedEvent.getJobId()).isEqualTo(jobId);
            assertThat(emittedEvent.getChapterId()).isEqualTo(chapterId);
            assertThat(emittedEvent.getBookId()).isEqualTo(bookId);
            assertThat(emittedEvent.getChunkCount()).isEqualTo(4); // 2 chunks per scene * 2 scenes
        }

        @Test
        @DisplayName("Should skip chunking when chunks already exist (idempotency)")
        void handleScenesDetected_existingChunks_skipChunking() {
            // Given
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(true);
            when(contentPersistencePort.countChunksByChapterId(chapterId)).thenReturn(5);

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            verify(textChunkingService, never()).extractChunks(anyString());
            verify(contentPersistencePort, never()).addChunksToScene(any(), any());
            
            ArgumentCaptor<ChunksCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ChunksCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getChunkCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should update job status during processing")
        void handleScenesDetected_updatesJobStatus() {
            // Given
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(Collections.emptyList());

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            verify(ingestionJobService, atLeastOnce()).updateJobStatus(
                    eq(jobId), eq(IngestionStatus.EMBEDDING_CHUNKS), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should emit IngestionFailedEvent on chunking error")
        void handleScenesDetected_chunkingError_emitsFailure() {
            // Given
            Scene scene = createScene(sceneId1, 0, 0L, 32L);
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(List.of(scene));
            when(textChunkingService.extractChunks(anyString()))
                    .thenThrow(new RuntimeException("Chunking algorithm failed"));

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            IngestionFailedEvent failedEvent = eventCaptor.getValue();
            assertThat(failedEvent.getJobId()).isEqualTo(jobId);
            assertThat(failedEvent.getFailedStage()).isEqualTo("CHUNKING");
            assertThat(failedEvent.getErrorMessage()).contains("Chunking algorithm failed");
        }

        @Test
        @DisplayName("Should handle chapter not found error")
        void handleScenesDetected_chapterNotFound_emitsFailure() {
            // Given
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.empty());

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getFailedStage()).isEqualTo("CHUNKING");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty chapter text")
        void handleScenesDetected_emptyText_emitsZeroChunks() {
            // Given
            testChapter.setRawText("");
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            ArgumentCaptor<ChunksCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ChunksCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getChunkCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle null chapter text")
        void handleScenesDetected_nullText_emitsZeroChunks() {
            // Given
            testChapter.setRawText(null);
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            ArgumentCaptor<ChunksCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ChunksCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getChunkCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle no scenes")
        void handleScenesDetected_noScenes_emitsZeroChunks() {
            // Given
            when(contentPersistencePort.chunksExistForChapter(chapterId)).thenReturn(false);
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(Collections.emptyList());

            // When
            handler.handleScenesDetected(testEvent);

            // Then
            verify(textChunkingService, never()).extractChunks(anyString());
            
            ArgumentCaptor<ChunksCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ChunksCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getChunkCount()).isEqualTo(0);
        }
    }

    private Scene createScene(UUID id, int index, Long startOffset, Long endOffset) {
        Scene scene = new Scene();
        scene.setId(id);
        scene.setSceneIndex(index);
        scene.setStartCharacterOffset(startOffset);
        scene.setEndCharacterOffset(endOffset);
        return scene;
    }

    private List<Chunk> createChunks(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    Chunk chunk = new Chunk();
                    chunk.setText("Chunk " + i);
                    chunk.setStartCharInChapter(i * 10);
                    chunk.setEndCharInChapter((i + 1) * 10);
                    chunk.setChunkNumberInChapter(i + 1);
                    return chunk;
                })
                .toList();
    }
}
