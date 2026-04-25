package com.lorevault.api.ingestion;

import com.lorevault.api.ingestion.application.resolution.ChapterEventResolutionHandler;
import com.lorevault.api.ingestion.application.resolution.ChapterEventResolutionService;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterEventResolutionHandler")
class ChapterEventResolutionHandlerTest {

    @Mock
    private ChapterEventResolutionService chapterEventResolutionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChapterEventResolutionHandler handler;

    @Test
    @DisplayName("Resolves chapter events automatically when scenes are detected")
    void resolvesChapterEventsOnScenesDetected() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(UUID.randomUUID()));
        when(chapterEventResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterEventResolutionResult(chapterId, true, 3, 2, "ok"));

        handler.handleScenesDetected(event);

        verify(chapterEventResolutionService).resolveChapter(chapterId);
        verify(eventPublisher).publishEvent(any(ChapterEventsResolvedEvent.class));
    }
}
