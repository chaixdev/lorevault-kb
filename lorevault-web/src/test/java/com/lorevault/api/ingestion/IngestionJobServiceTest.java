package com.lorevault.api.ingestion;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.ingestion.job.*;
import com.lorevault.api.library.book.PublicationCoordinates;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.ingestion.job.IngestionJobGraphRepository;
import com.lorevault.api.content.scene.SceneGraphRepository;

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

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionJobService")
class IngestionJobServiceTest {

    @Mock private ChunkGraphRepository chunkRepo;
    @Mock private SceneGraphRepository sceneRepo;
    @Mock private ChapterGraphRepository chapterRepo;
    @Mock private IngestionJobGraphRepository jobRepo;

    @Captor
    private ArgumentCaptor<IngestionJob> jobCaptor;

    private IngestionJobService ingestionJobService;

    private final UUID testChapterId = UUID.randomUUID();
    private final UUID testJobId = UUID.randomUUID();
    private final LocalDateTime testTimestamp = LocalDateTime.of(2023, 8, 26, 10, 0, 0);

    @BeforeEach
    void setUp() {
        ingestionJobService = new IngestionJobService(chunkRepo, sceneRepo, chapterRepo, jobRepo);
    }

    @Nested
    @DisplayName("Job Lifecycle Operations")
    class LifecycleOperations {

        @Test
        @DisplayName("Should create ingestion job")
        void shouldCreateIngestionJob() {
            IngestionJob mockPersistedJob = new IngestionJob();
            mockPersistedJob.setId(testJobId);
            mockPersistedJob.setChapterId(testChapterId);
            mockPersistedJob.setCreatedAt(testTimestamp);

            when(jobRepo.save(any(IngestionJob.class))).thenReturn(mockPersistedJob);

            IngestionJob result = ingestionJobService.createIngestionJob(testChapterId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testJobId);
            assertThat(result.getChapterId()).isEqualTo(testChapterId);

            verify(jobRepo).save(jobCaptor.capture());
            IngestionJob capturedJob = jobCaptor.getValue();
            assertThat(capturedJob.getId()).isNotNull();
            assertThat(capturedJob.getChapterId()).isEqualTo(testChapterId);
        }

        @Test
        @DisplayName("Should log job completion")
        void shouldLogJobComplete() {
            IngestionJob job = createTestJob();
            int chapterLength = 5000;
            int chunkCount = 25;

            when(chunkRepo.countByChapterIdViaScenes(testChapterId)).thenReturn(chunkCount);

            ingestionJobService.logJobComplete(null, testChapterId, chapterLength);

            verify(chunkRepo).countByChapterIdViaScenes(testChapterId);
        }

        @Test
        @DisplayName("Should log job failure")
        void shouldLogJobFailed() {
            IngestionJob job = createTestJob();
            String errorMessage = "Processing failed due to invalid content format";

            ingestionJobService.logJobFailed(job, errorMessage);
            // No-op — only logs; no state changes to assert
        }

        @Test
        @DisplayName("Should log job failure with cleanup and remove partial data")
        void shouldLogJobFailedWithCleanup() {
            IngestionJob job = createTestJob();
            String errorMessage = "Critical processing error";
            int deletedChunks = 5;

            when(chunkRepo.countByChapterIdViaScenes(testChapterId)).thenReturn(deletedChunks);
            doNothing().when(chunkRepo).deleteByChapterIdViaScenes(testChapterId);
            when(sceneRepo.findByChapterId(testChapterId)).thenReturn(List.of(
                    new Scene(),
                    new Scene(),
                    new Scene()
            ));
            doNothing().when(sceneRepo).deleteByChapterId(testChapterId);

            ingestionJobService.logJobFailedWithCleanup(job, errorMessage);

            verify(chunkRepo).deleteByChapterIdViaScenes(testChapterId);
            verify(sceneRepo).deleteByChapterId(testChapterId);
        }

        @Test
        @DisplayName("Should update job status with custom properties")
        void shouldUpdateJobStatusWithCustomProperties() {
            Map<String, Object> properties = Map.of(
                    "step", "scene_detection",
                    "progress", 75
            );

            ingestionJobService.updateJobStatus(testJobId, IngestionStatus.SCENE_SEGMENTATION, 
                    "Scene segmentation in progress", properties);
            // No-op — only logs; no state changes to assert
        }
    }

    @Nested
    @DisplayName("Job Query Operations")
    class QueryOperations {

        @Test
        @DisplayName("Should retrieve job status")
        void shouldRetrieveJobStatus() {
            IngestionJob job = createTestJob();

            when(jobRepo.findById(testJobId)).thenReturn(Optional.of(job));

            Optional<JobStatusDetails> response = ingestionJobService.getJobStatus(testJobId);

            assertThat(response).isPresent();
            JobStatusDetails jobStatus = response.get();
            assertThat(jobStatus.jobId()).isEqualTo(testJobId);
            assertThat(jobStatus.chapterId()).isEqualTo(testChapterId);
        }

        @Test
        @DisplayName("Should return empty when job not found")
        void shouldReturnEmptyWhenJobNotFound() {
            when(jobRepo.findById(testJobId)).thenReturn(Optional.empty());

            Optional<JobStatusDetails> response = ingestionJobService.getJobStatus(testJobId);

            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("Should list jobs with universe filter and pagination")
        void shouldListJobsWithUniverseFilterAndPagination() {
            String universe = "TestUniverse";
            String status = "SCENE_SEGMENTATION";
            int limit = 10;
            int offset = 0;

            List<Chapter> chapters = List.of(createTestChapter());
            List<IngestionJob> jobs = List.of(createTestJobWithStatus());

            when(chapterRepo.findAll()).thenReturn(chapters);
            when(jobRepo.findByChapterIdIn(any())).thenReturn(jobs);
            when(chapterRepo.findById(testChapterId)).thenReturn(Optional.of(createTestChapter()));

            PaginatedJobSummaries response = ingestionJobService.listJobs(universe, status, limit, offset);

            assertThat(response).isNotNull();
            assertThat(response.jobs()).hasSize(1);
            assertThat(response.pagination().total()).isEqualTo(1);
            assertThat(response.pagination().limit()).isEqualTo(limit);
            assertThat(response.pagination().offset()).isEqualTo(offset);

            JobSummary jobSummary = response.jobs().get(0);
            assertThat(jobSummary.jobId()).isEqualTo(testJobId);
            assertThat(jobSummary.chapterId()).isEqualTo(testChapterId);
            assertThat(jobSummary.status()).isEqualTo(IngestionStatus.SCENE_SEGMENTATION);
        }

        @Test
        @DisplayName("Should list all jobs when no filters applied")
        void shouldListAllJobsWhenNoFilters() {
            List<IngestionJob> allJobs = List.of(
                    createTestJobWithStatus(),
                    createCompletedJob()
            );

            when(jobRepo.findAll()).thenReturn(allJobs);
            when(chapterRepo.findById(any())).thenReturn(Optional.of(createTestChapter()));

            PaginatedJobSummaries response = ingestionJobService.listJobs(null, null, 20, 0);

            assertThat(response.jobs()).hasSize(2);
            assertThat(response.pagination().total()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should filter active jobs correctly")
        void shouldFilterActiveJobsCorrectly() {
            List<IngestionJob> jobs = List.of(
                    createTestJobWithStatus(),
                    createCompletedJob(),
                    createFailedJob()
            );

            when(jobRepo.findAll()).thenReturn(jobs);
            when(chapterRepo.findById(any())).thenReturn(Optional.of(createTestChapter()));

            PaginatedJobSummaries response = ingestionJobService.listJobs(null, "ACTIVE", 20, 0);

            assertThat(response.jobs()).hasSize(1);
            assertThat(response.jobs().get(0).status()).isEqualTo(IngestionStatus.SCENE_SEGMENTATION);
        }
    }

    private IngestionJob createTestJob() {
        IngestionJob job = new IngestionJob();
        job.setId(testJobId);
        job.setChapterId(testChapterId);
        job.setCreatedAt(testTimestamp);
        return job;
    }

    private IngestionJob createTestJobWithStatus() {
        return createTestJob();
    }

    private IngestionJob createCompletedJob() {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(testTimestamp);
        job.setCompletedAt(testTimestamp.plusMinutes(30));
        return job;
    }

    private IngestionJob createFailedJob() {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setChapterId(UUID.randomUUID());
        job.setCreatedAt(testTimestamp);
        job.setCompletedAt(testTimestamp.plusMinutes(10));
        return job;
    }

    private Chapter createTestChapter() {
        PublicationCoordinates coords =
                new PublicationCoordinates(
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
