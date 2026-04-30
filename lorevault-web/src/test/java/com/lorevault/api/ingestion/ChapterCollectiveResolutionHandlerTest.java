package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionHandler;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionResult;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionService;
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
@DisplayName("ChapterCollectiveResolutionHandler")
class ChapterCollectiveResolutionHandlerTest {

    @Mock
    private ChapterCollectiveResolutionService chapterCollectiveResolutionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IngestionJobService ingestionJobService;

    @InjectMocks
    private ChapterCollectiveResolutionHandler handler;

    @Test
    @DisplayName("Publishes chapter collectives resolved event when scenes are detected")
    void publishesChapterCollectivesResolvedOnScenesDetected() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ScenesDetectedEvent event = new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(UUID.randomUUID()));

        when(chapterCollectiveResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterCollectiveResolutionResult(chapterId, true, 4, 2, "ok"));

        handler.handleScenesDetected(event);

        verify(chapterCollectiveResolutionService).resolveChapter(chapterId);

        ArgumentCaptor<ChapterCollectivesResolvedEvent> captor = ArgumentCaptor.forClass(ChapterCollectivesResolvedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ChapterCollectivesResolvedEvent published = captor.getValue();

        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
        assertThat(published.isProcessed()).isTrue();
        assertThat(published.getMentionCount()).isEqualTo(4);
        assertThat(published.getChapterCollectiveCount()).isEqualTo(2);

        verify(eventPublisher).publishEvent(any(ChapterCollectivesResolvedEvent.class));
    }
}
