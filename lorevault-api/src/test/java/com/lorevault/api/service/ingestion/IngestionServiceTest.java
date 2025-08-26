package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.JobContextPort;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.service.content.ChunkEmbeddingService;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.ScenePersistenceService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.timeline.DefaultTemporalEdgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for IngestionService covering all consolidated functionality:
 * - Chapter submission and validation (formerly ChapterValidationService)
 * - Workflow orchestration (formerly IngestionWorkflowService) 
 * - Job management integration (via IngestionJobService)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService Tests")
class IngestionServiceTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JobContextPort jobContextPort;
    @Mock private SceneDetectionService sceneDetectionService;
    @Mock private ScenePersistenceService scenePersistenceService;
    @Mock private TextChunkingService textChunkingService;
    @Mock private ChunkEmbeddingService chunkEmbeddingService;
    @Mock private DefaultTemporalEdgeService defaultTemporalEdgeService;

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
    @DisplayName("Workflow Orchestration Tests")
    class WorkflowOrchestrationTests {

        @Test
        @DisplayName("Should execute complete workflow for new chapter")
        void processChapter_newChapter_executesCompleteWorkflow() {
            // Given
            List<Scene> scenes = createTestScenes(2);
            List<SceneWithCoordinates> scenesWithCoords = createTestScenesWithCoordinates(2);
            List<Chunk> chunks = createTestChunks(3);

            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(sceneDetectionService.detectScenesForChapter(chapterId)).thenReturn(scenesWithCoords);
            when(scenePersistenceService.persistDetectedScenes(chapterId, scenesWithCoords)).thenReturn(scenes);
            when(textChunkingService.extractChunks(anyString())).thenReturn(chunks);
            when(chunkEmbeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(6);

            // When
            ingestionService.processChapter(testJob, testChapter);

            // Then
            verify(jobContextPort).setCurrentJobId(jobId);
            verify(sceneDetectionService).detectScenesForChapter(chapterId);
            verify(scenePersistenceService).persistDetectedScenes(chapterId, scenesWithCoords);
            verify(defaultTemporalEdgeService).createAllDefaults(bookId);
            verify(textChunkingService, times(2)).extractChunks(anyString());
            verify(contentPersistencePort, times(2)).addChunksToScene(any(), any());
            verify(chunkEmbeddingService).generateEmbeddingsForChapter(chapterId);
            verify(ingestionJobService).completeJob(testJob, chapterId, testChapter.getRawText().length());
            verify(jobContextPort).clearCurrentJobId();
        }

        @Test
        @DisplayName("Should use existing scenes when available")
        void processChapter_existingScenes_skipsSceneDetection() {
            // Given
            List<Scene> existingScenes = createTestScenes(1);
            List<Chunk> chunks = createTestChunks(2);

            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(existingScenes);
            when(textChunkingService.extractChunks(anyString())).thenReturn(chunks);
            when(chunkEmbeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(2);

            // When
            ingestionService.processChapter(testJob, testChapter);

            // Then
            verify(sceneDetectionService, never()).detectScenesForChapter(any());
            verify(scenePersistenceService, never()).persistDetectedScenes(any(), any());
            verify(defaultTemporalEdgeService, never()).createAllDefaults(any());
            verify(textChunkingService).extractChunks(anyString());
            verify(chunkEmbeddingService).generateEmbeddingsForChapter(chapterId);
            verify(ingestionJobService).completeJob(testJob, chapterId, testChapter.getRawText().length());
        }

        @Test
        @DisplayName("Should handle LLM errors with cleanup")
        void processChapter_llmError_failsJobWithCleanup() {
            // Given
            RuntimeException llmError = new RuntimeException("LLM API call failed");
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(sceneDetectionService.detectScenesForChapter(chapterId)).thenThrow(llmError);

            // When
            ingestionService.processChapter(testJob, testChapter);

            // Then
            verify(ingestionJobService).failJobWithCleanup(testJob, "LLM API call failed: LLM API call failed");
            verify(jobContextPort).clearCurrentJobId();
        }

        @Test
        @DisplayName("Should handle non-retryable errors without cleanup")
        void processChapter_nonRetryableError_failsJobWithoutCleanup() {
            // Given
            RuntimeException error = new RuntimeException("Database connection failed");
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(Collections.emptyList());
            when(sceneDetectionService.detectScenesForChapter(chapterId)).thenThrow(error);

            // When
            ingestionService.processChapter(testJob, testChapter);

            // Then
            verify(ingestionJobService).failJob(testJob, "Chapter processing failed: Database connection failed");
            verify(ingestionJobService, never()).failJobWithCleanup(any(), any());
            verify(jobContextPort).clearCurrentJobId();
        }

        @Test
        @DisplayName("Should handle empty chapter text gracefully")
        void processChapter_emptyChapterText_handlesGracefully() {
            // Given
            Chapter emptyChapter = new Chapter();
            emptyChapter.setId(chapterId);
            emptyChapter.setBookId(bookId);
            emptyChapter.setRawText(null);

            List<Scene> scenes = createTestScenes(1);
            when(contentPersistencePort.findScenesByChapterId(chapterId)).thenReturn(scenes);
            when(chunkEmbeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(0);

            // When
            ingestionService.processChapter(testJob, emptyChapter);

            // Then
            verify(textChunkingService, never()).extractChunks(any());
            verify(contentPersistencePort, never()).addChunksToScene(any(), any());
            verify(ingestionJobService).completeJob(testJob, chapterId, 0);
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

        @Test
        @DisplayName("Should ensure job context cleanup even on failure")
        void processChapter_anyError_clearsJobContext() {
            // Given
            when(contentPersistencePort.findScenesByChapterId(chapterId))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // When
            ingestionService.processChapter(testJob, testChapter);

            // Then
            verify(jobContextPort).setCurrentJobId(jobId);
            verify(jobContextPort).clearCurrentJobId();
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

    private List<Scene> createTestScenes(int count) {
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Scene scene = new Scene();
            scene.setId(UUID.randomUUID());
            scene.setChapter(testChapter);
            scene.setSceneIndex(i);
            scene.setStartCharacterOffset((long) (i * 20));
            scene.setEndCharacterOffset((long) ((i + 1) * 20));
            scene.setText("Test scene " + i);
            scenes.add(scene);
        }
        return scenes;
    }

    private List<SceneWithCoordinates> createTestScenesWithCoordinates(int count) {
        List<SceneWithCoordinates> scenes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SceneWithCoordinates scene = new SceneWithCoordinates(
                i + 1,  // sceneIndex (1-based)
                i * 20L, // startCharacterOffset
                (i + 1) * 20L, // endCharacterOffset
                "Test scene " + i // contextSummary
            );
            scenes.add(scene);
        }
        return scenes;
    }

    private List<Chunk> createTestChunks(int count) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Chunk chunk = new Chunk();
            chunk.setText("Test chunk " + i);
            chunk.setStartCharInChapter(i * 10);
            chunk.setEndCharInChapter((i + 1) * 10);
            chunk.setChunkNumberInChapter(i + 1);
            chunks.add(chunk);
        }
        return chunks;
    }
}