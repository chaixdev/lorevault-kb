package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for IngestionJobService covering both lifecycle and query operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionJobService")
class IngestionJobServiceTest {

    @Mock
    private ContentPersistencePort contentPersistencePort;

    @Captor
    private ArgumentCaptor<IngestionJob> jobCaptor;

    @Captor
    private ArgumentCaptor<StatusRecord> statusRecordCaptor;

    private IngestionJobService ingestionJobService;

    private final UUID testChapterId = UUID.randomUUID();
    private final UUID testJobId = UUID.randomUUID();
    private final LocalDateTime testTimestamp = LocalDateTime.of(2023, 8, 26, 10, 0, 0);

    @BeforeEach
    void setUp() {
        ingestionJobService = new IngestionJobService(contentPersistencePort);
    }

    @Nested
    @DisplayName("Job Lifecycle Operations")
    class LifecycleOperations {

        @Test
        @DisplayName("Should create ingestion job with initial status record")
        void shouldCreateIngestionJobWithInitialStatus() {
            // Arrange
            IngestionJob mockPersistedJob = new IngestionJob();
            mockPersistedJob.setId(testJobId);
            mockPersistedJob.setChapterId(testChapterId);
            mockPersistedJob.setCreatedAt(testTimestamp);

            when(contentPersistencePort.createJobWithChapter(any(IngestionJob.class), eq(testChapterId)))
                    .thenReturn(mockPersistedJob);

            // Act
            IngestionJob result = ingestionJobService.createIngestionJob(testChapterId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testJobId);
            assertThat(result.getChapterId()).isEqualTo(testChapterId);
            assertThat(result.getCurrentStatus()).isNotNull();
            assertThat(result.getCurrentStatus().getStatus()).isEqualTo(IngestionStatus.QUEUED);

            // Verify job creation
            verify(contentPersistencePort).createJobWithChapter(jobCaptor.capture(), eq(testChapterId));
            IngestionJob capturedJob = jobCaptor.getValue();
            assertThat(capturedJob.getId()).isNotNull();
            assertThat(capturedJob.getChapterId()).isEqualTo(testChapterId);

            // Verify status record creation
            verify(contentPersistencePort).addStatusRecord(eq(testJobId), statusRecordCaptor.capture());
            StatusRecord capturedStatus = statusRecordCaptor.getValue();
            assertThat(capturedStatus.getJobId()).isEqualTo(testJobId);
            assertThat(capturedStatus.getStatus()).isEqualTo(IngestionStatus.QUEUED);
        }

        @Test
        @DisplayName("Should complete job with success status and metadata")
        void shouldCompleteJobWithSuccessStatus() {
            // Arrange
            IngestionJob job = createTestJob();
            int chapterLength = 5000;
            int chunkCount = 25;

            when(contentPersistencePort.countChunksByChapterId(testChapterId)).thenReturn(chunkCount);
            when(contentPersistencePort.findJob(testJobId)).thenReturn(Optional.of(job));

            // Act
            ingestionJobService.completeJob(job, testChapterId, chapterLength);

            // Assert
            assertThat(job.getCurrentStatus().getStatus()).isEqualTo(IngestionStatus.COMPLETE);
            assertThat(job.getCurrentStatus().getProgressPercent()).isEqualTo(100);
            assertThat(job.getCompletedAt()).isNotNull();

            // Verify status record saved
            verify(contentPersistencePort).addStatusRecord(eq(testJobId), statusRecordCaptor.capture());
            StatusRecord completionStatus = statusRecordCaptor.getValue();
            assertThat(completionStatus.getStatus()).isEqualTo(IngestionStatus.COMPLETE);
            assertThat(completionStatus.getProperties()).containsEntry("chunkCount", chunkCount);
            assertThat(completionStatus.getProperties()).containsEntry("chapterLength", chapterLength);
        }

        @Test
        @DisplayName("Should fail job with error message")
        void shouldFailJobWithErrorMessage() {
            // Arrange
            IngestionJob job = createTestJob();
            String errorMessage = "Processing failed due to invalid content format";

            when(contentPersistencePort.findJob(testJobId)).thenReturn(Optional.of(job));

            // Act
            ingestionJobService.failJob(job, errorMessage);

            // Assert
            assertThat(job.getCurrentStatus().getStatus()).isEqualTo(IngestionStatus.FAILED);
            assertThat(job.getCurrentStatus().getStepDescription()).isEqualTo(errorMessage);
            assertThat(job.getCurrentStatus().getProgressPercent()).isEqualTo(0);
            assertThat(job.getCompletedAt()).isNotNull();

            // Verify status record saved
            verify(contentPersistencePort).addStatusRecord(eq(testJobId), any(StatusRecord.class));
        }

        @Test
        @DisplayName("Should fail job with cleanup and remove partial data")
        void shouldFailJobWithCleanup() {
            // Arrange
            IngestionJob job = createTestJob();
            String errorMessage = "Critical processing error";
            int deletedChunks = 5;
            int deletedScenes = 3;

            when(contentPersistencePort.deleteChunksByChapterId(testChapterId)).thenReturn(deletedChunks);
            when(contentPersistencePort.deleteScenesByChapterId(testChapterId)).thenReturn(deletedScenes);
            when(contentPersistencePort.findJob(testJobId)).thenReturn(Optional.of(job));

            // Act
            ingestionJobService.failJobWithCleanup(job, errorMessage);

            // Assert
            assertThat(job.getCurrentStatus().getStatus()).isEqualTo(IngestionStatus.FAILED);
            assertThat(job.getCurrentStatus().getStepDescription()).contains(errorMessage);
            assertThat(job.getCurrentStatus().getStepDescription()).contains("data cleaned up for retry");

            // Verify cleanup operations
            verify(contentPersistencePort).deleteChunksByChapterId(testChapterId);
            verify(contentPersistencePort).deleteScenesByChapterId(testChapterId);
        }

        @Test
        @DisplayName("Should update job status with custom properties")
        void shouldUpdateJobStatusWithCustomProperties() {
            // Arrange
            Map<String, Object> properties = Map.of(
                    "step", "scene_detection",
                    "progress", 75
            );

            // Act
            ingestionJobService.updateJobStatus(testJobId, IngestionStatus.SCENE_SEGMENTATION, 
                    "Scene segmentation in progress", properties);

            // Assert
            verify(contentPersistencePort).addStatusRecord(eq(testJobId), statusRecordCaptor.capture());
            StatusRecord statusRecord = statusRecordCaptor.getValue();
            assertThat(statusRecord.getStatus()).isEqualTo(IngestionStatus.SCENE_SEGMENTATION);
            assertThat(statusRecord.getStepDescription()).isEqualTo("Scene segmentation in progress");
            assertThat(statusRecord.getProperties()).containsAllEntriesOf(properties);
        }
    }

    @Nested
    @DisplayName("Job Query Operations")
    class QueryOperations {

        @Test
        @DisplayName("Should retrieve job status with recent updates")
        void shouldRetrieveJobStatusWithRecentUpdates() {
            // Arrange
            IngestionJob job = createTestJobWithStatus();
            List<StatusRecord> statusHistory = createStatusHistory();

            when(contentPersistencePort.findJob(testJobId)).thenReturn(Optional.of(job));
            when(contentPersistencePort.findStatusHistoryForJob(testJobId)).thenReturn(statusHistory);

            // Act
            Optional<JobStatusResponse> response = ingestionJobService.getJobStatus(testJobId);

            // Assert
            assertThat(response).isPresent();
            JobStatusResponse jobStatus = response.get();
            assertThat(jobStatus.getJobId()).isEqualTo(testJobId);
            assertThat(jobStatus.getChapterId()).isEqualTo(testChapterId);
            assertThat(jobStatus.getCurrentStatus()).isEqualTo(IngestionStatus.SCENE_SEGMENTATION);
            assertThat(jobStatus.getProgressPercent()).isEqualTo(75);
            assertThat(jobStatus.getRecentUpdates()).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty when job not found")
        void shouldReturnEmptyWhenJobNotFound() {
            // Arrange
            when(contentPersistencePort.findJob(testJobId)).thenReturn(Optional.empty());

            // Act
            Optional<JobStatusResponse> response = ingestionJobService.getJobStatus(testJobId);

            // Assert
            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("Should list jobs with universe filter and pagination")
        void shouldListJobsWithUniverseFilterAndPagination() {
            // Arrange
            String universe = "TestUniverse";
            String status = "SCENE_SEGMENTATION";
            int limit = 10;
            int offset = 0;

            List<Chapter> chapters = List.of(createTestChapter());
            List<IngestionJob> jobs = List.of(createTestJobWithStatus());

            when(contentPersistencePort.findChaptersByUniverse(universe)).thenReturn(chapters);
            when(contentPersistencePort.findJobsByChapterIds(any())).thenReturn(jobs);
            when(contentPersistencePort.findChapterById(testChapterId)).thenReturn(Optional.of(createTestChapter()));

            // Act
            JobListResponse response = ingestionJobService.listJobs(universe, status, limit, offset);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getJobs()).hasSize(1);
            assertThat(response.getPagination().getTotal()).isEqualTo(1);
            assertThat(response.getPagination().getLimit()).isEqualTo(limit);
            assertThat(response.getPagination().getOffset()).isEqualTo(offset);

            JobListResponse.JobSummary jobSummary = response.getJobs().get(0);
            assertThat(jobSummary.getJobId()).isEqualTo(testJobId);
            assertThat(jobSummary.getChapterId()).isEqualTo(testChapterId);
            assertThat(jobSummary.getStatus()).isEqualTo(IngestionStatus.SCENE_SEGMENTATION);
        }

        @Test
        @DisplayName("Should list all jobs when no filters applied")
        void shouldListAllJobsWhenNoFilters() {
            // Arrange
            List<IngestionJob> allJobs = List.of(
                    createTestJobWithStatus(),
                    createCompletedJob()
            );

            when(contentPersistencePort.findAllJobs()).thenReturn(allJobs);
            when(contentPersistencePort.findChapterById(any())).thenReturn(Optional.of(createTestChapter()));

            // Act
            JobListResponse response = ingestionJobService.listJobs(null, null, 20, 0);

            // Assert
            assertThat(response.getJobs()).hasSize(2);
            assertThat(response.getPagination().getTotal()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should filter active jobs correctly")
        void shouldFilterActiveJobsCorrectly() {
            // Arrange
            List<IngestionJob> jobs = List.of(
                    createTestJobWithStatus(), // PROCESSING (active)
                    createCompletedJob(),      // COMPLETE (inactive)
                    createFailedJob()          // FAILED (inactive)
            );

            when(contentPersistencePort.findAllJobs()).thenReturn(jobs);
            when(contentPersistencePort.findChapterById(any())).thenReturn(Optional.of(createTestChapter()));

            // Act
            JobListResponse response = ingestionJobService.listJobs(null, "ACTIVE", 20, 0);

            // Assert
            assertThat(response.getJobs()).hasSize(1);
            assertThat(response.getJobs().get(0).getStatus()).isEqualTo(IngestionStatus.SCENE_SEGMENTATION);
        }
    }

    // Helper methods for test data creation

    private IngestionJob createTestJob() {
        IngestionJob job = new IngestionJob();
        job.setId(testJobId);
        job.setChapterId(testChapterId);
        job.setCreatedAt(testTimestamp);
        return job;
    }

    private IngestionJob createTestJobWithStatus() {
        IngestionJob job = createTestJob();
        StatusRecord currentStatus = new StatusRecord(
                UUID.randomUUID(),
                testJobId,
                testTimestamp,
                IngestionStatus.SCENE_SEGMENTATION,
                "Processing chapter content",
                75,
                Map.of("step", "scene_detection")
        );
        job.setCurrentStatus(currentStatus);
        return job;
    }

    private IngestionJob createCompletedJob() {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(testTimestamp);
        job.setCompletedAt(testTimestamp.plusMinutes(30));
        
        StatusRecord completedStatus = new StatusRecord(
                UUID.randomUUID(),
                job.getId(),
                testTimestamp.plusMinutes(30),
                IngestionStatus.COMPLETE,
                "Chapter processing completed",
                100,
                Map.of("chunkCount", 20)
        );
        job.setCurrentStatus(completedStatus);
        return job;
    }

    private IngestionJob createFailedJob() {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(testTimestamp);
        job.setCompletedAt(testTimestamp.plusMinutes(10));
        
        StatusRecord failedStatus = new StatusRecord(
                UUID.randomUUID(),
                job.getId(),
                testTimestamp.plusMinutes(10),
                IngestionStatus.FAILED,
                "Processing failed",
                0,
                Map.of("error", "format_error")
        );
        job.setCurrentStatus(failedStatus);
        return job;
    }

    private List<StatusRecord> createStatusHistory() {
        StatusRecord queuedRecord = new StatusRecord(UUID.randomUUID(), testJobId, testTimestamp,
                IngestionStatus.QUEUED, "Job queued", 0, Map.of());
        StatusRecord detectingRecord = new StatusRecord(UUID.randomUUID(), testJobId, testTimestamp.plusMinutes(5),
                IngestionStatus.SCENE_SEGMENTATION, "Processing started", 25, Map.of());
        
        return List.of(queuedRecord, detectingRecord);
    }

    private Chapter createTestChapter() {
        com.lorevault.api.dto.shared.PublicationCoordinates coords = 
                new com.lorevault.api.dto.shared.PublicationCoordinates(
                        "TestUniverse", 
                        "TestSeries", 
                        "TestBook", 
                        "Test Chapter", 
                        1, 
                        1);
        
        Chapter chapter = new Chapter();
        chapter.setId(testChapterId);
        chapter.setChapterTitle("Test Chapter");
        chapter.setCoordinates(coords);
        return chapter;
    }
}