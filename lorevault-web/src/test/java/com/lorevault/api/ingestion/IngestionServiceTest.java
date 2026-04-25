package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionIsolatedLookupService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.ChapterPersistenceException;
import com.lorevault.api.ingestion.domain.ChapterSubmissionLookupException;

import com.lorevault.api.library.domain.Book;
import com.lorevault.api.content.entities.Chapter;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.application.result.IngestionSubmissionResult;
import com.lorevault.api.ingestion.application.result.JobStatusDetails;
import com.lorevault.api.ingestion.application.result.PaginatedJobSummaries;
import com.lorevault.api.web.command.ingestion.SubmitChapterRequest;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import com.lorevault.api.library.infrastructure.BookGraphRepository;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;

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

    @Mock private ChapterGraphRepository chapterRepo;
    @Mock private BookGraphRepository bookRepo;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private IngestionJobGraphRepository jobRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private IngestionIsolatedLookupService isolatedLookup;

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
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.empty());
            when(bookRepo.findById(bookId)).thenReturn(Optional.of(testBook));
            when(chapterRepo.save(any(Chapter.class))).thenReturn(testChapter);
            when(ingestionJobService.createIngestionJob(chapterId)).thenReturn(testJob);

            IngestionSubmissionResult response = ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()
            );

            assertThat(response.jobId()).isEqualTo(jobId);
            assertThat(response.chapterId()).isEqualTo(chapterId);

            verify(chapterRepo).save(any(Chapter.class));
            verify(ingestionJobService).createIngestionJob(chapterId);
            verify(eventPublisher).publishEvent(any(ChapterIngestionEvent.class));
        }

        @Test
        @DisplayName("Should return existing job for duplicate content with active job")
        void submitChapter_duplicateContentWithActiveJob_returnsExistingJob() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(isolatedLookup.existsActiveForChapter(chapterId)).thenReturn(true);
            when(isolatedLookup.findMostRecentJobId(chapterId)).thenReturn(Optional.of(jobId));

            IngestionSubmissionResult response = ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()
            );

            assertThat(response.jobId()).isEqualTo(jobId);
            assertThat(response.chapterId()).isEqualTo(chapterId);

            verify(chapterRepo, never()).save(any());
            verify(ingestionJobService, never()).createIngestionJob(any());
        }

        @Test
        @DisplayName("Should create new job for duplicate content without active job")
        void submitChapter_duplicateContentNoActiveJob_createsNewJob() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(isolatedLookup.existsActiveForChapter(chapterId)).thenReturn(false);
            when(ingestionJobService.createIngestionJob(chapterId)).thenReturn(testJob);

            IngestionSubmissionResult response = ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()
            );

            assertThat(response.jobId()).isEqualTo(jobId);
            assertThat(response.chapterId()).isEqualTo(chapterId);

            verify(chapterRepo, never()).save(any());
            verify(ingestionJobService).createIngestionJob(chapterId);
            verify(eventPublisher).publishEvent(any(ChapterIngestionEvent.class));
        }

        @Test
        @DisplayName("Should throw exception when book not found")
        void submitChapter_bookNotFound_throwsException() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.empty());
            when(bookRepo.findById(bookId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Book not found");
        }

        @Test
        @DisplayName("Should handle chapter creation failure gracefully")
        void submitChapter_chapterCreationFails_throwsException() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.empty());
            when(bookRepo.findById(bookId)).thenReturn(Optional.of(testBook));
            when(chapterRepo.save(any(Chapter.class)))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()
            ))
                    .isInstanceOf(ChapterPersistenceException.class)
                    .hasMessageContaining("Failed to create chapter in graph");
        }
    }

    @Nested
    @DisplayName("Job Management Integration Tests")
    class JobManagementIntegrationTests {

        @Test
        @DisplayName("Should delegate job status to IngestionJobService")
        void getJobStatus_delegatesToJobService() {
            JobStatusDetails expectedResponse = new JobStatusDetails(
                    jobId,
                    chapterId,
                    bookId,
                    IngestionStatus.QUEUED,
                    0,
                    false,
                    null,
                    null,
                    java.util.List.of(),
                    null);
            when(ingestionJobService.getJobStatus(jobId)).thenReturn(Optional.of(expectedResponse));

            Optional<JobStatusDetails> result = ingestionService.getJobStatus(jobId);

            assertThat(result).isPresent().contains(expectedResponse);
            verify(ingestionJobService).getJobStatus(jobId);
        }

        @Test
        @DisplayName("Should delegate job listing to IngestionJobService")
        void listJobs_delegatesToJobService() {
            PaginatedJobSummaries expectedResponse = new PaginatedJobSummaries(
                    java.util.List.of(),
                    new PaginatedJobSummaries.Pagination(0, 10, 0, false));
            when(ingestionJobService.listJobs("universe", "COMPLETED", 10, 0)).thenReturn(expectedResponse);

            PaginatedJobSummaries result = ingestionService.listJobs("universe", "COMPLETED", 10, 0);

            assertThat(result).isEqualTo(expectedResponse);
            verify(ingestionJobService).listJobs("universe", "COMPLETED", 10, 0);
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should fail closed when active job exists but recent job id is missing")
        void submitChapter_missingActiveJobId_throwsTypedException() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(isolatedLookup.existsActiveForChapter(chapterId)).thenReturn(true);
            when(isolatedLookup.findMostRecentJobId(chapterId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()))
                    .isInstanceOf(ChapterSubmissionLookupException.class)
                    .hasMessageContaining("could not be resolved");

            verify(ingestionJobService, never()).createIngestionJob(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Should surface active job lookup failure instead of creating new job")
        void submitChapter_activeJobLookupFailure_throwsTypedException() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(isolatedLookup.existsActiveForChapter(chapterId))
                    .thenThrow(new ChapterSubmissionLookupException(
                            IngestionFailure.builder(
                                            "CHAPTER_ACTIVE_JOB_LOOKUP_FAILED",
                                            "Chapter submission lookup failed during hasActiveJobForChapter: Connection failed")
                                    .exceptionType("RuntimeException")
                                    .stage("CHAPTER_SUBMISSION")
                                    .detail("chapterId", chapterId)
                                    .detail("lookupType", "activeJob")
                                    .build(),
                            new RuntimeException("Connection failed")));

            assertThatThrownBy(() -> ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()))
                    .isInstanceOf(ChapterSubmissionLookupException.class)
                    .hasMessageContaining("hasActiveJobForChapter");

            verify(ingestionJobService, never()).createIngestionJob(any());
        }

        @Test
        @DisplayName("Should surface content hash lookup failure instead of treating chapter as new")
        void submitChapter_contentHashLookupFailure_throwsTypedException() {
            when(isolatedLookup.findChapterByContentHash(anyString()))
                    .thenThrow(new ChapterSubmissionLookupException(
                            IngestionFailure.builder(
                                            "CHAPTER_HASH_LOOKUP_FAILED",
                                            "Chapter submission lookup failed during findChapterByContentHash: Hash lookup failed")
                                    .exceptionType("RuntimeException")
                                    .stage("CHAPTER_SUBMISSION")
                                    .detail("lookupType", "contentHash")
                                    .detail("contentHash", "testhash123")
                                    .build(),
                            new RuntimeException("Hash lookup failed")));

            assertThatThrownBy(() -> ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()))
                    .isInstanceOf(ChapterSubmissionLookupException.class)
                    .hasMessageContaining("findChapterByContentHash");

            verify(chapterRepo, never()).save(any());
            verify(ingestionJobService, never()).createIngestionJob(any());
        }

        @Test
        @DisplayName("Should surface recent job lookup failure instead of creating duplicate work")
        void submitChapter_recentJobLookupFailure_throwsTypedException() {
            when(isolatedLookup.findChapterByContentHash(anyString())).thenReturn(Optional.of(testChapter));
            when(isolatedLookup.existsActiveForChapter(chapterId)).thenReturn(true);
            when(isolatedLookup.findMostRecentJobId(chapterId))
                    .thenThrow(new ChapterSubmissionLookupException(
                            IngestionFailure.builder(
                                            "CHAPTER_RECENT_JOB_LOOKUP_FAILED",
                                            "Chapter submission lookup failed during findMostRecentJobForChapter: Recent job lookup failed")
                                    .exceptionType("RuntimeException")
                                    .stage("CHAPTER_SUBMISSION")
                                    .detail("chapterId", chapterId)
                                    .detail("lookupType", "recentJob")
                                    .build(),
                            new RuntimeException("Recent job lookup failed")));

            assertThatThrownBy(() -> ingestionService.submitChapter(
                    testRequest.getBookId(),
                    testRequest.getChapterNumber(),
                    testRequest.getChapterTitle(),
                    testRequest.getChapterText()))
                    .isInstanceOf(ChapterSubmissionLookupException.class)
                    .hasMessageContaining("findMostRecentJobForChapter");

            verify(ingestionJobService, never()).createIngestionJob(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

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
