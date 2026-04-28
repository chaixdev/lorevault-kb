package com.lorevault.api.ingestion;

import com.lorevault.api.ingestion.resolution.location.ChapterLocationResolutionResult;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import java.util.List;
import java.util.UUID;

import com.lorevault.api.ingestion.resolution.location.ChapterLocationResolutionHandler;
import com.lorevault.api.ingestion.resolution.location.ChapterLocationResolutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterLocationResolutionHandler")
class ChapterLocationResolutionHandlerTest {

    @Mock
    private ChapterLocationResolutionService chapterLocationResolutionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChapterLocationResolutionHandler handler;

    @Test
    @DisplayName("Resolves chapter locations automatically when scenes are detected")
    void resolvesChapterLocationsOnScenesDetected() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(UUID.randomUUID()));

        when(chapterLocationResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterLocationResolutionResult(chapterId, true, 4, 2, "ok"));

        handler.handleScenesDetected(event);

        verify(chapterLocationResolutionService).resolveChapter(chapterId);
        verify(eventPublisher).publishEvent(any(ChapterLocationsResolvedEvent.class));

        ArgumentCaptor<ChapterLocationsResolvedEvent> captor = ArgumentCaptor.forClass(ChapterLocationsResolvedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ChapterLocationsResolvedEvent published = captor.getValue();
        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
    }
}
