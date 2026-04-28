package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.events.BookEventCandidatesGeneratedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterEventEmbeddingHandler")
class ChapterEventEmbeddingHandlerTest {

    @Mock private ChapterEventEmbeddingService embeddingService;
    @Mock private ChapterEventEmbeddingTransactionSupport txSupport;
    @Mock private BookEventAnnCandidateService annCandidateService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChapterEventEmbeddingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChapterEventEmbeddingHandler(
                embeddingService,
                txSupport,
                annCandidateService,
                ingestionJobService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Embeds events, generates ANN pairs, and publishes fan-in event")
    void embedsGeneratesCandidatesAndPublishesEvent() {
        UUID jobId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(embeddingService.embedChapterEvents(chapterId)).thenReturn(2);
        when(txSupport.loadChapterEvents(chapterId)).thenReturn(List.of());
        when(annCandidateService.generateCandidates(List.of(), chapterId)).thenReturn(List.of(
                BookEventCandidatePair.of(UUID.randomUUID(), UUID.randomUUID(), 0.91)
        ));

        handler.handleChapterEventsResolved(new ChapterEventsResolvedEvent(
                this,
                jobId,
                correlationId,
                chapterId,
                bookId,
                true,
                5,
                2,
                0
        ));

        verify(ingestionJobService).updateJobStatus(
                eq(jobId),
                eq(IngestionStatus.EVENT_CANDIDATE_GENERATION),
                anyString(),
                anyMap()
        );
        verify(embeddingService).embedChapterEvents(chapterId);
        verify(txSupport).loadChapterEvents(chapterId);
        verify(annCandidateService).generateCandidates(List.of(), chapterId);

        ArgumentCaptor<ApplicationEvent> publishedCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(publishedCaptor.capture());

        assertThat(publishedCaptor.getValue()).isInstanceOf(BookEventCandidatesGeneratedEvent.class);
        BookEventCandidatesGeneratedEvent published = (BookEventCandidatesGeneratedEvent) publishedCaptor.getValue();
        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getCorrelationId()).isEqualTo(correlationId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
        assertThat(published.getEmbeddedCount()).isEqualTo(2);
        assertThat(published.getCandidatePairCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Embedding failure is converted to failed job status")
    void embeddingFailureMarksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(embeddingService.embedChapterEvents(chapterId)).thenThrow(new RuntimeException("embedding API timeout"));

        handler.handleChapterEventsResolved(new ChapterEventsResolvedEvent(
                this,
                jobId,
                correlationId,
                chapterId,
                bookId,
                true,
                5,
                2,
                0
        ));

        verify(ingestionJobService).updateJobStatus(eq(jobId), eq(IngestionStatus.FAILED), anyString(), anyMap());
        ArgumentCaptor<IngestionFailedEvent> failedCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(failedCaptor.capture());
        assertThat(failedCaptor.getValue().getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("ANN failure is converted to failed job status and does not publish fan-in event")
    void annFailureMarksJobFailedAndDoesNotPublishFanInEvent() {
        UUID jobId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(embeddingService.embedChapterEvents(chapterId)).thenReturn(1);
        when(txSupport.loadChapterEvents(chapterId)).thenReturn(List.of());
        when(annCandidateService.generateCandidates(List.of(), chapterId))
                .thenThrow(new RuntimeException("vector index unavailable"));

        handler.handleChapterEventsResolved(new ChapterEventsResolvedEvent(
                this,
                jobId,
                correlationId,
                chapterId,
                bookId,
                true,
                5,
                2,
                0
        ));

        verify(ingestionJobService).updateJobStatus(eq(jobId), eq(IngestionStatus.FAILED), anyString(), anyMap());
        ArgumentCaptor<IngestionFailedEvent> failedCaptor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(failedCaptor.capture());
        assertThat(failedCaptor.getValue().getCorrelationId()).isEqualTo(correlationId);
        verify(eventPublisher, never()).publishEvent(any(BookEventCandidatesGeneratedEvent.class));
    }
}
