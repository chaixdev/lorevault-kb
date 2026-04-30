package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.job.IngestionStatus;
import com.lorevault.api.ingestion.events.BookEventCandidatesGeneratedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.content.association.ChapterEvent;
import java.util.List;
import java.util.Map;
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
    @Mock private BookEventMergeVerificationService mergeVerificationService;
    @Mock private BookEventReductionService bookEventReductionService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChapterEventEmbeddingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChapterEventEmbeddingHandler(
                embeddingService,
                txSupport,
                annCandidateService,
                mergeVerificationService,
                bookEventReductionService,
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
        UUID crossChapterEventId = UUID.randomUUID();
        ChapterEvent current = chapterEvent(chapterId, UUID.randomUUID());
        ChapterEvent crossChapter = chapterEvent(UUID.randomUUID(), crossChapterEventId);
        BookEventCandidatePair candidatePair = BookEventCandidatePair.of(current.id(), crossChapterEventId, 0.91);
        List<UUID> expectedCandidateEndpointIds = List.of(candidatePair.eventId1(), candidatePair.eventId2());

        when(embeddingService.embedChapterEvents(chapterId)).thenReturn(2);
        when(txSupport.loadChapterEvents(chapterId)).thenReturn(List.of(current));
        when(annCandidateService.generateCandidates(List.of(current), chapterId)).thenReturn(List.of(candidatePair));
        when(txSupport.loadChapterEventsByIds(expectedCandidateEndpointIds)).thenReturn(List.of(crossChapter, current));
        when(mergeVerificationService.verifyCandidates(eq(jobId), eq(chapterId), any(), any())).thenReturn(List.of(
                new EventMergeModels.EventMergeVerification(
                        current.id(), crossChapterEventId, EventMergeModels.MergeDecision.KEEP_SEPARATE, 0.77, "not same"
                )
        ));
        when(bookEventReductionService.reduceAndPersist(eq(jobId), eq(chapterId), eq(bookId), any(), any()))
                .thenReturn(new BookEventReductionService.BookEventReductionResult(1, 2));

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
        verify(annCandidateService).generateCandidates(List.of(current), chapterId);
        verify(txSupport).loadChapterEventsByIds(expectedCandidateEndpointIds);
        verify(mergeVerificationService).verifyCandidates(eq(jobId), eq(chapterId), any(), any());
        verify(bookEventReductionService).reduceAndPersist(eq(jobId), eq(chapterId), eq(bookId), any(), any());

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
        assertThat(published.getBookEventsCreated()).isEqualTo(1);
    }

    private ChapterEvent chapterEvent(UUID chapterId, UUID eventId) {
        return new ChapterEvent(
                eventId,
                chapterId,
                null,
                "Display",
                "display",
                "TYPE",
                1,
                "aggregate",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
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
