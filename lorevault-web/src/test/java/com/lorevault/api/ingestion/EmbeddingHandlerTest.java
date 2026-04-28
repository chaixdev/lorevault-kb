package com.lorevault.api.ingestion;

import com.lorevault.api.ai.embedding.EmbeddingFailure;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import com.lorevault.api.ingestion.content.EmbeddingHandler;
import com.lorevault.api.ingestion.job.IngestionJobGraphRepository;
import com.lorevault.api.ingestion.job.IngestionJobService;

import com.lorevault.api.ai.embedding.EmbeddingService;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.chunk.ChunkGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.content.scene.SceneGraphRepository;
import com.lorevault.api.ingestion.events.ChunksCreatedEvent;
import com.lorevault.api.ingestion.events.EmbeddingsCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingHandler")
class EmbeddingHandlerTest {

    @Mock
    private ChapterGraphRepository chapterRepo;

    @Mock
    private ChunkGraphRepository chunkRepo;

    @Mock
    private SceneGraphRepository sceneRepo;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private IngestionJobService ingestionJobService;

    @Mock
    private IngestionJobGraphRepository jobRepo;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Publishes embeddings-completed event after successful embedding generation")
    void handleChunksCreated_successPublishesEmbeddingsCompletedEvent() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setRawText("abcdefghijklmnopqrstuvwxyz0123456789");

        Scene scene = new Scene(
                UUID.randomUUID(),
                0,
                0L,
                10L,
                "ctx",
                null,
                null,
                null,
                "text",
                chapterId,
                null,
                null,
                null,
                null,
                null
        );

        when(embeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(1);
        when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of(scene));
        when(chunkRepo.countByChapterIdViaScenes(chapterId)).thenReturn(1);
        when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(chapter));

        EmbeddingHandler handler = new EmbeddingHandler(
                chapterRepo,
                chunkRepo,
                sceneRepo,
                embeddingService,
                ingestionJobService,
                jobRepo,
                eventPublisher
        );

        handler.handleChunksCreated(new ChunksCreatedEvent(this, jobId, chapterId, UUID.randomUUID(), 1));

        ArgumentCaptor<EmbeddingsCompletedEvent> eventCaptor = ArgumentCaptor.forClass(EmbeddingsCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        EmbeddingsCompletedEvent published = eventCaptor.getValue();
        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getTotalScenes()).isEqualTo(1);
        assertThat(published.getTotalChunks()).isEqualTo(1);
        assertThat(published.getTotalEmbeddings()).isEqualTo(1);
        assertThat(published.getChapterLength()).isEqualTo(36);

        verify(ingestionJobService, atLeastOnce()).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.EMBEDDING_CHUNKS),
                contains("embeddings"),
                anyMap()
        );
        verify(ingestionJobService, never()).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("EMBEDDING failed"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Marks job failed when embedding completion tracking throws")
    void handleChunksCreated_whenCompletionTrackingThrows_marksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        when(embeddingService.generateEmbeddingsForChapter(chapterId)).thenReturn(1);
        when(sceneRepo.findByChapterId(chapterId)).thenThrow(new IllegalStateException("scene count failed"));

        EmbeddingHandler handler = new EmbeddingHandler(
                chapterRepo,
                chunkRepo,
                sceneRepo,
                embeddingService,
                ingestionJobService,
                jobRepo,
                eventPublisher
        );

        handler.handleChunksCreated(new ChunksCreatedEvent(this, jobId, chapterId, UUID.randomUUID(), 1));

        ArgumentCaptor<IngestionFailedEvent> failureCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(failureCaptor.capture());

        IngestionFailedEvent failedEvent = failureCaptor.getValue();
        assertThat(failedEvent.getJobId()).isEqualTo(jobId);
        assertThat(failedEvent.getChapterId()).isEqualTo(chapterId);
        assertThat(failedEvent.getFailedStage()).isEqualTo("EMBEDDING");
        assertThat(failedEvent.getErrorMessage()).isEqualTo("scene count failed");

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("EMBEDDING failed"),
                anyMap()
        );
        verify(eventPublisher, never()).publishEvent(argThat(event -> event instanceof EmbeddingsCompletedEvent));
    }

    @Test
    @DisplayName("Marks job failed when embedding backend is unavailable")
    void handleChunksCreated_whenEmbeddingBackendFails_marksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        EmbeddingFailure failure = EmbeddingFailure.builder(
                        "EMBEDDING_BACKEND_UNAVAILABLE",
                        "Embedding backend failed while generating chunk vectors")
                .exceptionType(EmbeddingGenerationException.class.getSimpleName())
                .stage("EMBEDDING")
                .detail("chapterId", chapterId)
                .build();
        when(embeddingService.generateEmbeddingsForChapter(chapterId))
                .thenThrow(new EmbeddingGenerationException(failure, new RuntimeException("Connection failed")));

        EmbeddingHandler handler = new EmbeddingHandler(
                chapterRepo,
                chunkRepo,
                sceneRepo,
                embeddingService,
                ingestionJobService,
                jobRepo,
                eventPublisher
        );

        handler.handleChunksCreated(new ChunksCreatedEvent(this, jobId, chapterId, UUID.randomUUID(), 1));

        ArgumentCaptor<IngestionFailedEvent> failureCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(failureCaptor.capture());

        IngestionFailedEvent failedEvent = failureCaptor.getValue();
        assertThat(failedEvent.getJobId()).isEqualTo(jobId);
        assertThat(failedEvent.getChapterId()).isEqualTo(chapterId);
        assertThat(failedEvent.getFailedStage()).isEqualTo("EMBEDDING");
        assertThat(failedEvent.getErrorMessage()).contains("Embedding backend failed");
        assertThat(failedEvent.isRetryable()).isTrue();

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("EMBEDDING failed"),
                anyMap()
        );
        verify(eventPublisher, never()).publishEvent(argThat(event -> event instanceof EmbeddingsCompletedEvent));
    }

    @Test
    @DisplayName("Marks job failed when embedding response is empty for non-empty work")
    void handleChunksCreated_whenEmbeddingResponseEmpty_marksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        EmbeddingFailure failure = EmbeddingFailure.builder(
                        "EMBEDDING_RESPONSE_EMPTY",
                        "Embedding backend returned no vectors for requested chunks")
                .exceptionType(EmbeddingGenerationException.class.getSimpleName())
                .stage("EMBEDDING")
                .detail("chapterId", chapterId)
                .build();
        when(embeddingService.generateEmbeddingsForChapter(chapterId))
                .thenThrow(new EmbeddingGenerationException(failure));

        EmbeddingHandler handler = new EmbeddingHandler(
                chapterRepo,
                chunkRepo,
                sceneRepo,
                embeddingService,
                ingestionJobService,
                jobRepo,
                eventPublisher
        );

        handler.handleChunksCreated(new ChunksCreatedEvent(this, jobId, chapterId, UUID.randomUUID(), 1));

        ArgumentCaptor<IngestionFailedEvent> failureCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(failureCaptor.capture());

        IngestionFailedEvent failedEvent = failureCaptor.getValue();
        assertThat(failedEvent.getFailedStage()).isEqualTo("EMBEDDING");
        assertThat(failedEvent.getErrorMessage()).contains("returned no vectors");
        assertThat(failedEvent.isRetryable()).isFalse();

        verify(eventPublisher, never()).publishEvent(argThat(event -> event instanceof EmbeddingsCompletedEvent));
    }
}
