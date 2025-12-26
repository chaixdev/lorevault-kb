package com.lorevault.api.handler;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.event.ingestion.ChapterPersistedEvent;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionPipelineStarter Tests")
class IngestionPipelineStarterTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IngestionPipelineStarter starter;

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

        testEvent = new ChapterIngestionEvent(this, jobId, chapterId);
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should emit ChapterPersistedEvent on receiving ChapterIngestionEvent")
        void handleChapterIngestion_emitsChapterPersistedEvent() {
            // Given
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            starter.handleChapterIngestion(testEvent);

            // Then
            ArgumentCaptor<ChapterPersistedEvent> eventCaptor = ArgumentCaptor.forClass(ChapterPersistedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            ChapterPersistedEvent emittedEvent = eventCaptor.getValue();
            assertThat(emittedEvent.getJobId()).isEqualTo(jobId);
            assertThat(emittedEvent.getChapterId()).isEqualTo(chapterId);
            assertThat(emittedEvent.getBookId()).isEqualTo(bookId);
        }

        @Test
        @DisplayName("Should look up chapter to get bookId")
        void handleChapterIngestion_looksUpChapter() {
            // Given
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(testChapter));

            // When
            starter.handleChapterIngestion(testEvent);

            // Then
            verify(contentPersistencePort).findChapterById(chapterId);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not emit event when chapter not found")
        void handleChapterIngestion_chapterNotFound_noEventEmitted() {
            // Given
            when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.empty());

            // When
            starter.handleChapterIngestion(testEvent);

            // Then
            verify(eventPublisher, never()).publishEvent(any(ChapterPersistedEvent.class));
        }

        @Test
        @DisplayName("Should handle database errors gracefully")
        void handleChapterIngestion_databaseError_noEventEmitted() {
            // Given
            when(contentPersistencePort.findChapterById(chapterId))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When - should not throw
            starter.handleChapterIngestion(testEvent);

            // Then
            verify(eventPublisher, never()).publishEvent(any(ChapterPersistedEvent.class));
        }
    }
}
