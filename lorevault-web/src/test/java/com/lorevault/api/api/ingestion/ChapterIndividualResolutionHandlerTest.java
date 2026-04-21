package com.lorevault.api.ingestion;

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

import com.lorevault.api.support.ChapterIndividualResolutionResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterIndividualResolutionHandler")
class ChapterIndividualResolutionHandlerTest {

    @Mock
    private ChapterIndividualResolutionService chapterIndividualResolutionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChapterIndividualResolutionHandler handler;

    @Test
    @DisplayName("Resolves chapter individuals automatically when scenes are detected")
    void resolvesChapterOnScenesDetected() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(UUID.randomUUID()));
        when(chapterIndividualResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterIndividualResolutionResponse(chapterId, true, 2, 1, "ok"));

        handler.handleScenesDetected(event);

        verify(chapterIndividualResolutionService).resolveChapter(chapterId);
        verify(eventPublisher).publishEvent(any(ChapterIndividualsResolvedEvent.class));
    }
}
