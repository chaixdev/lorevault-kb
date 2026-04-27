package com.lorevault.api.ingestion;

import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.coref.EventCoreferenceService;
import com.lorevault.api.ingestion.application.resolution.ChapterEventResolutionHandler;
import com.lorevault.api.ingestion.application.resolution.ChapterEventResolutionService;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;
import com.lorevault.api.ingestion.domain.coref.EventCorefModels;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterEventResolutionHandler")
class ChapterEventResolutionHandlerTest {

    @Mock private EventCoreferenceService eventCoreferenceService;
    @Mock private ChapterEventResolutionService chapterEventResolutionService;
    @Mock private ChapterGraphRepository chapterGraphRepository;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChapterEventResolutionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChapterEventResolutionHandler(
                eventCoreferenceService,
                chapterEventResolutionService,
                chapterGraphRepository,
                ingestionJobService,
                eventPublisher
        );
    }

    private ScenesDetectedEvent scenesDetectedEvent(UUID jobId, UUID chapterId, UUID bookId) {
        return new ScenesDetectedEvent(this, jobId, UUID.randomUUID(), chapterId, bookId, List.of(UUID.randomUUID()));
    }

    private EventCorefModels.CorefPassResult passResult(UUID chapterId) {
        return new EventCorefModels.CorefPassResult(chapterId, "pass-1", "model", 3, 2);
    }

    @Test
    @DisplayName("Runs Stage 2 then Stage 3 and publishes ChapterEventsResolvedEvent on success")
    void runsBothStagesAndPublishesEvent() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(chapterGraphRepository.hasCompletedEventResolutionForJob(chapterId, jobId)).thenReturn(false);

        when(eventCoreferenceService.runCorefPass(anyList(), eq(chapterId), eq(jobId))).thenReturn(passResult(chapterId));
        when(chapterEventResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterEventResolutionResult(chapterId, true, 3, 2, "ok"));

        handler.handleScenesDetected(scenesDetectedEvent(jobId, chapterId, bookId));

        verify(eventCoreferenceService).runCorefPass(anyList(), eq(chapterId), eq(jobId));
        verify(chapterEventResolutionService).resolveChapter(chapterId);

        ArgumentCaptor<ApplicationEvent> publishedCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(publishedCaptor.capture());
        assertThat(publishedCaptor.getValue()).isInstanceOf(ChapterEventsResolvedEvent.class);

        ChapterEventsResolvedEvent published = (ChapterEventsResolvedEvent) publishedCaptor.getValue();
        assertThat(published.isProcessed()).isTrue();
        assertThat(published.getMentionCount()).isEqualTo(3);
        assertThat(published.getChapterEventCount()).isEqualTo(2);
        assertThat(published.getCorrelationId()).isNotNull();
        verify(chapterGraphRepository).markEventResolutionCompleted(chapterId, jobId);
    }

    @Test
    @DisplayName("Aborts Stage 3 and does not publish when Stage 2 throws")
    void abortsWhenStage2Throws() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(chapterGraphRepository.hasCompletedEventResolutionForJob(chapterId, jobId)).thenReturn(false);

        when(eventCoreferenceService.runCorefPass(anyList(), eq(chapterId), eq(jobId)))
                .thenThrow(new RuntimeException("LLM timed out"));

        handler.handleScenesDetected(scenesDetectedEvent(jobId, chapterId, bookId));

        verify(chapterEventResolutionService, never()).resolveChapter(any());
        // IngestionFailedEvent published via PipelineStageSupport — not a ChapterEventsResolvedEvent
        verify(eventPublisher, never()).publishEvent(any(ChapterEventsResolvedEvent.class));
    }

    @Test
    @DisplayName("Does not publish success event when Stage 3 throws")
    void doesNotPublishWhenStage3Throws() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(chapterGraphRepository.hasCompletedEventResolutionForJob(chapterId, jobId)).thenReturn(false);

        when(eventCoreferenceService.runCorefPass(anyList(), eq(chapterId), eq(jobId))).thenReturn(passResult(chapterId));
        when(chapterEventResolutionService.resolveChapter(chapterId))
                .thenThrow(new RuntimeException("graph write failed"));

        handler.handleScenesDetected(scenesDetectedEvent(jobId, chapterId, bookId));

        verify(eventPublisher, never()).publishEvent(any(ChapterEventsResolvedEvent.class));
    }

    @Test
    @DisplayName("Publishes event even when Stage 3 returns non-success result (no-op chapter)")
    void publishesEventForNoOpChapter() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(chapterGraphRepository.hasCompletedEventResolutionForJob(chapterId, jobId)).thenReturn(false);

        when(eventCoreferenceService.runCorefPass(anyList(), eq(chapterId), eq(jobId))).thenReturn(passResult(chapterId));
        when(chapterEventResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterEventResolutionResult(chapterId, false, 0, 0, "No mentions"));

        handler.handleScenesDetected(scenesDetectedEvent(jobId, chapterId, bookId));

        ArgumentCaptor<ApplicationEvent> publishedCaptor2 = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(publishedCaptor2.capture());
        assertThat(publishedCaptor2.getValue()).isInstanceOf(ChapterEventsResolvedEvent.class);

        ChapterEventsResolvedEvent published2 = (ChapterEventsResolvedEvent) publishedCaptor2.getValue();
        assertThat(published2.isProcessed()).isFalse();
    }

    @Test
    @DisplayName("duplicateScenesDetectedEvent_skippedAfterCompletion")
    void duplicateScenesDetectedEventSkippedAfterCompletion() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(chapterGraphRepository.hasCompletedEventResolutionForJob(chapterId, jobId)).thenReturn(false, true);
        when(eventCoreferenceService.runCorefPass(anyList(), eq(chapterId), eq(jobId))).thenReturn(passResult(chapterId));
        when(chapterEventResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterEventResolutionResult(chapterId, true, 3, 2, "ok"));

        ScenesDetectedEvent event = scenesDetectedEvent(jobId, chapterId, bookId);
        handler.handleScenesDetected(event);
        handler.handleScenesDetected(event);

        verify(eventCoreferenceService, times(1)).runCorefPass(anyList(), eq(chapterId), eq(jobId));
        verify(chapterEventResolutionService, times(1)).resolveChapter(chapterId);
    }
}
