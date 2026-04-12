package com.lorevault.api.ingestion;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterIndividualResolutionHandler")
class ChapterIndividualResolutionHandlerTest {

    @Mock
    private ChapterIndividualResolutionService chapterIndividualResolutionService;

    @InjectMocks
    private ChapterIndividualResolutionHandler handler;

    @Test
    @DisplayName("Resolves chapter individuals automatically when scenes are detected")
    void resolvesChapterOnScenesDetected() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(UUID.randomUUID()));

        handler.handleScenesDetected(event);

        verify(chapterIndividualResolutionService).resolveChapter(chapterId);
    }
}
