package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.event.ChapterIngestionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for IngestionService covering:
 * - Chapter submission and validation
 * - Job management integration
 * - Event publishing for async pipeline
 * 
 * Note: Workflow orchestration tests have moved to handler tests:
 * - SceneDetectionHandlerTest
 * - ChunkingHandlerTest  
 * - EmbeddingHandlerTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService Tests")
class IngestionServiceTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IngestionService ingestionService;

    private UUID bookId;
    private UUID chapterId;
    private UUID jobId;
    private Book testBook;
    private Chapter testChapter;
    private IngestionJob testJob;
    private SubmitChapterRequest testRequest;

    @BeforeEach
    void setUp() {
        bookId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        testBook = createTestBook();
        testChapter = createTestChapter();
        testJob = createTestJob();
        testRequest = createTestRequest();
    }

    @Nested
    @DisplayName("Chapter Submission Tests")
    class ChapterSubmissionTests {

        @Test
        @DisplayName("Should create new chapter and job for fresh content")
        void submitChapter_newContent_createsChapterAndJob() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.empty());
            when(contentPersistencePort.findBookById(bookId)).thenReturn(Optional.of(testBook));
            when(contentPersistencePort.createChapter(any(Chapter.class))).thenReturn(testChapter);
            when(ingestionJobService.createIngestionJob(chapterId)).thenReturn(testJob);

            // When
            SubmitChapterResponse response = ingestionService.submitChapter(testRequest);

            // Then
            assertThat(response.getJobId()).isEqualTo(jobId);
            assertThat(response.getChapterId()).isEqualTo(chapterId);
            assertThat(response.getMessage()).isNotNull();

            verify(contentPersistencePort).createChapter(any(Chapter.class));
            verify(ingestionJobService).createIngestionJob(chapterId);
            verify(eventPublisher).publishEvent(any(ChapterIngestionEvent.class));
        }

        @Test
        @DisplayName("Should return existing job for duplicate content with active job")
        void submitChapter_duplicateContentWithActiveJob_returnsExistingJob() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.hasActiveJobForChapter(chapterId)).thenReturn(true);
            when(contentPersistencePort.findMostRecentJobForChapter(chapterId)).thenReturn(Optional.of(testJob));

            // When
            SubmitChapterResponse response = ingestionService.submitChapter(testRequest);

            // Then
            assertThat(response.getJobId()).isEqualTo(jobId);
            assertThat(response.getChapterId()).isEqualTo(chapterId);
            assertThat(response.getMessage()).isNotNull();

            verify(contentPersistencePort, never()).createChapter(any());
            verify(ingestionJobService, never()).createIngestionJob(any());
        }

        @Test
        @DisplayName("Should create new job for duplicate content without active job")
        void submitChapter_duplicateContentNoActiveJob_createsNewJob() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.hasActiveJobForChapter(chapterId)).thenReturn(false);
            when(ingestionJobService.createIngestionJob(chapterId)).thenReturn(testJob);

            // When
            SubmitChapterResponse response = ingestionService.submitChapter(testRequest);

            // Then
            assertThat(response.getJobId()).isEqualTo(jobId);
            assertThat(response.getChapterId()).isEqualTo(chapterId);
            assertThat(response.getMessage()).isNotNull();

            verify(contentPersistencePort, never()).createChapter(any());
            verify(ingestionJobService).createIngestionJob(chapterId);
            verify(eventPublisher).publishEvent(any(ChapterIngestionEvent.class));
        }

        @Test
        @DisplayName("Should throw exception when book not found")
        void submitChapter_bookNotFound_throwsException() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.empty());
            when(contentPersistencePort.findBookById(bookId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> ingestionService.submitChapter(testRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Book not found");
        }

        @Test
        @DisplayName("Should handle chapter creation failure gracefully")
        void submitChapter_chapterCreationFails_throwsException() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.empty());
            when(contentPersistencePort.findBookById(bookId)).thenReturn(Optional.of(testBook));
            when(contentPersistencePort.createChapter(any(Chapter.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> ingestionService.submitChapter(testRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to create chapter in graph");
        }
    }

    @Nested
    @DisplayName("Job Management Integration Tests")
    class JobManagementIntegrationTests {

        @Test
        @DisplayName("Should delegate job status to IngestionJobService")
        void getJobStatus_delegatesToJobService() {
            // Given
            JobStatusResponse expectedResponse = new JobStatusResponse();
            when(ingestionJobService.getJobStatus(jobId)).thenReturn(Optional.of(expectedResponse));

            // When
            Optional<JobStatusResponse> result = ingestionService.getJobStatus(jobId);

            // Then
            assertThat(result).isPresent().contains(expectedResponse);
            verify(ingestionJobService).getJobStatus(jobId);
        }

        @Test
        @DisplayName("Should delegate job listing to IngestionJobService")
        void listJobs_delegatesToJobService() {
            // Given
            JobListResponse expectedResponse = new JobListResponse();
            when(ingestionJobService.listJobs("universe", "COMPLETED", 10, 0)).thenReturn(expectedResponse);

            // When
            JobListResponse result = ingestionService.listJobs("universe", "COMPLETED", 10, 0);

            // Then
            assertThat(result).isEqualTo(expectedResponse);
            verify(ingestionJobService).listJobs("universe", "COMPLETED", 10, 0);
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle missing active job gracefully")
        void submitChapter_missingActiveJob_createsNewJob() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.hasActiveJobForChapter(chapterId)).thenReturn(true);
            when(contentPersistencePort.findMostRecentJobForChapter(chapterId)).thenReturn(Optional.empty());
            when(ingestionJobService.createIngestionJob(chapterId)).thenReturn(testJob);

            // When
            SubmitChapterResponse response = ingestionService.submitChapter(testRequest);

            // Then
            assertThat(response.getJobId()).isEqualTo(jobId);
            assertThat(response.getMessage()).isNotNull();
            verify(ingestionJobService).createIngestionJob(chapterId);
        }

        @Test
        @DisplayName("Should handle persistence port exceptions gracefully")
        void submitChapter_persistenceError_handlesGracefully() {
            // Given
            when(contentPersistencePort.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(contentPersistencePort.hasActiveJobForChapter(chapterId))
                    .thenThrow(new RuntimeException("Connection failed"));
            when(ingestionJobService.createIngestionJob(chapterId)).thenReturn(testJob);

            // When
            SubmitChapterResponse response = ingestionService.submitChapter(testRequest);

            // Then
            assertThat(response.getJobId()).isEqualTo(jobId);
            assertThat(response.getMessage()).isNotNull();
            verify(ingestionJobService).createIngestionJob(chapterId);
        }
    }

    // Test data creation helper methods
    private Book createTestBook() {
        Book book = new Book();
        book.setId(bookId);
        book.setTitle("Test Book");
        book.setUniverse("Test Universe");
        book.setSeries("Test Series");
        book.setBookNumber(1);
        book.setUniverseId(UUID.randomUUID());
        book.setSeriesId(UUID.randomUUID());
        return book;
    }

    private Chapter createTestChapter() {
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setChapterTitle("Test Chapter");
        chapter.setRawText("This is test chapter content that is long enough to be meaningful for testing purposes.");
        chapter.setContentHash("testhash123");
        return chapter;
    }

    private IngestionJob createTestJob() {
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(chapterId);
        
        StatusRecord statusRecord = new StatusRecord();
        statusRecord.setStatus(IngestionStatus.QUEUED);
        job.setCurrentStatus(statusRecord);
        
        return job;
    }

    private SubmitChapterRequest createTestRequest() {
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setBookId(bookId);
        request.setChapterNumber(1);
        request.setChapterTitle("Test Chapter");
        request.setChapterText("This is test chapter content that is long enough to be meaningful for testing purposes.");
        return request;
    }
}
