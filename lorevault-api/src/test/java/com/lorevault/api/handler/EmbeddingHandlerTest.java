package com.lorevault.api.handler;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.event.ingestion.ChunksCreatedEvent;
import com.lorevault.api.event.ingestion.IngestionCompletedEvent;
import com.lorevault.api.event.ingestion.IngestionFailedEvent;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChunkGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.IngestionJobGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SceneGraphRepository;
import com.lorevault.api.service.content.EmbeddingService;
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
@DisplayName("EmbeddingHandler Tests")
class EmbeddingHandlerTest {

    @Mock private ChapterGraphRepository chapterRepo;
    @Mock private ChunkGraphRepository chunkRepo;
    @Mock private SceneGraphRepository sceneRepo;
    @Mock private EmbeddingService embeddingService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private IngestionJobGraphRepository jobRepo;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmbeddingHandler handler;

    private UUID jobId;
    private UUID chapterId;
    private UUID bookId;
    private ChunksCreatedEvent testEvent;
    private Chapter testChapter;
    private IngestionJob testJob;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        testEvent = new ChunksCreatedEvent(this, jobId, chapterId, bookId, 10);
        
        testChapter = new Chapter();
        testChapter.setId(chapterId);
        testChapter.setBookId(bookId);
        testChapter.setRawText("Test chapter content");
        
        testJob = new IngestionJob();
        testJob.setId(jobId);
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should generate embeddings and emit IngestionCompletedEvent")
        void handleChunksCreated_generatesEmbeddingsSuccessfully() {
            when(embeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(10);
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of());
            when(chunkRepo.countByChapterIdViaScenes(chapterId)).thenReturn(10);
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(testJob));

            handler.handleChunksCreated(testEvent);

            verify(embeddingService).generateEmbeddingsForChapter(chapterId);
            verify(ingestionJobService).completeJob(testJob, chapterId, testChapter.getRawText().length());

            ArgumentCaptor<IngestionCompletedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            IngestionCompletedEvent emittedEvent = eventCaptor.getValue();
            assertThat(emittedEvent.getJobId()).isEqualTo(jobId);
            assertThat(emittedEvent.getChapterId()).isEqualTo(chapterId);
            assertThat(emittedEvent.getTotalEmbeddings()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should update job status during processing")
        void handleChunksCreated_updatesJobStatus() {
            when(embeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(5);

            handler.handleChunksCreated(testEvent);

            verify(ingestionJobService, atLeastOnce()).updateJobStatus(
                    eq(jobId), eq(IngestionStatus.EMBEDDING_CHUNKS), anyString(), any());
        }

        @Test
        @DisplayName("Should handle zero chunks gracefully")
        void handleChunksCreated_zeroChunks_emitsEvent() {
            ChunksCreatedEvent zeroChunksEvent = new ChunksCreatedEvent(this, jobId, chapterId, bookId, 0);
            when(embeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(0);
            when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of());
            when(chunkRepo.countByChapterIdViaScenes(chapterId)).thenReturn(0);
            when(chunkRepo.countByChapterId(chapterId)).thenReturn(0);
            when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(testChapter));
            when(jobRepo.findById(jobId)).thenReturn(Optional.of(testJob));

            handler.handleChunksCreated(zeroChunksEvent);

            ArgumentCaptor<IngestionCompletedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getTotalEmbeddings()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should emit IngestionFailedEvent on embedding API error")
        void handleChunksCreated_apiError_emitsFailure() {
            when(embeddingService.generateEmbeddingsForChapter(chapterId))
                    .thenThrow(new RuntimeException("Embedding API timeout"));

            handler.handleChunksCreated(testEvent);

            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            
            IngestionFailedEvent failedEvent = eventCaptor.getValue();
            assertThat(failedEvent.getJobId()).isEqualTo(jobId);
            assertThat(failedEvent.getFailedStage()).isEqualTo("EMBEDDING");
            assertThat(failedEvent.getErrorMessage()).contains("Embedding API timeout");
            assertThat(failedEvent.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should emit retryable failure for rate limit errors")
        void handleChunksCreated_rateLimitError_emitsRetryableFailure() {
            when(embeddingService.generateEmbeddingsForChapter(chapterId))
                    .thenThrow(new RuntimeException("rate limit exceeded"));

            handler.handleChunksCreated(testEvent);

            ArgumentCaptor<IngestionFailedEvent> eventCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should update job status to FAILED on error")
        void handleChunksCreated_error_updatesJobToFailed() {
            when(embeddingService.generateEmbeddingsForChapter(chapterId))
                    .thenThrow(new RuntimeException("Database error"));

            handler.handleChunksCreated(testEvent);

            verify(ingestionJobService).updateJobStatus(
                    eq(jobId), eq(IngestionStatus.FAILED), contains("EMBEDDING failed"), any());
        }
    }
}
