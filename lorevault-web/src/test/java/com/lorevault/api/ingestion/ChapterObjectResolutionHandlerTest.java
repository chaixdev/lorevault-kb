package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionHandler;
import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionResult;
import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterObjectResolutionHandler")
class ChapterObjectResolutionHandlerTest {

    @Mock
    private ChapterObjectResolutionService chapterObjectResolutionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IngestionJobService ingestionJobService;

    @InjectMocks
    private ChapterObjectResolutionHandler handler;

    @Test
    @DisplayName("Publishes chapter objects resolved event when scenes are detected")
    void publishesChapterObjectsResolvedOnScenesDetected() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(UUID.randomUUID()));

        when(chapterObjectResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterObjectResolutionResult(chapterId, true, 4, 2, "ok"));

        handler.handleScenesDetected(event);

        verify(chapterObjectResolutionService).resolveChapter(chapterId);

        ArgumentCaptor<ChapterObjectsResolvedEvent> captor = ArgumentCaptor.forClass(ChapterObjectsResolvedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ChapterObjectsResolvedEvent published = captor.getValue();

        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
        assertThat(published.isProcessed()).isTrue();
        assertThat(published.getMentionCount()).isEqualTo(4);
        assertThat(published.getChapterObjectCount()).isEqualTo(2);

        verify(eventPublisher).publishEvent(any(ChapterObjectsResolvedEvent.class));
    }
}
