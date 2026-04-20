package com.lorevault.api.ingestion;

import com.lorevault.api.ai.EmbeddingService;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.content.Scene;
import com.lorevault.api.content.SceneGraphRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanWrapperImpl;
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
        BeanWrapperImpl chapterBean = new BeanWrapperImpl(chapter);
        chapterBean.setPropertyValue("id", chapterId);
        chapterBean.setPropertyValue("rawText", "abcdefghijklmnopqrstuvwxyz0123456789");

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

        BeanWrapperImpl eventBean = new BeanWrapperImpl(eventCaptor.getValue());
        assertThat(eventBean.getPropertyValue("jobId")).isEqualTo(jobId);
        assertThat(eventBean.getPropertyValue("chapterId")).isEqualTo(chapterId);
        assertThat(eventBean.getPropertyValue("totalScenes")).isEqualTo(1);
        assertThat(eventBean.getPropertyValue("totalChunks")).isEqualTo(1);
        assertThat(eventBean.getPropertyValue("totalEmbeddings")).isEqualTo(1);
        assertThat(eventBean.getPropertyValue("chapterLength")).isEqualTo(36);

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

        BeanWrapperImpl failureBean = new BeanWrapperImpl(failureCaptor.getValue());
        assertThat(failureBean.getPropertyValue("jobId")).isEqualTo(jobId);
        assertThat(failureBean.getPropertyValue("chapterId")).isEqualTo(chapterId);
        assertThat(failureBean.getPropertyValue("failedStage")).isEqualTo("EMBEDDING");
        assertThat(failureBean.getPropertyValue("errorMessage")).isEqualTo("scene count failed");

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.FAILED),
                contains("EMBEDDING failed"),
                anyMap()
        );
        verify(eventPublisher, never()).publishEvent(argThat(event -> event instanceof EmbeddingsCompletedEvent));
    }
}
