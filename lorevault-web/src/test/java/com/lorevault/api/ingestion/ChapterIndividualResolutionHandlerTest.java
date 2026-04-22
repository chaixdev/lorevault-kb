package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

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

import com.lorevault.api.ingestion.application.result.ChapterIndividualResolutionResult;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;

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
                .thenReturn(new ChapterIndividualResolutionResult(chapterId, true, 2, 1, "ok"));

        handler.handleScenesDetected(event);

        verify(chapterIndividualResolutionService).resolveChapter(chapterId);
        verify(eventPublisher).publishEvent(any(ChapterIndividualsResolvedEvent.class));
    }
}
