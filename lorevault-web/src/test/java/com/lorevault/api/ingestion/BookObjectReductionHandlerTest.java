package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.ingestion.events.BookObjectsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterObjectsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import com.lorevault.api.ingestion.resolution.object.BookObjectReductionHandler;
import com.lorevault.api.ingestion.resolution.object.BookObjectReductionService;
import com.lorevault.api.ingestion.resolution.object.BookObjectResolutionResult;
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
@DisplayName("BookObjectReductionHandler")
class BookObjectReductionHandlerTest {

    @Mock
    private BookObjectReductionService bookObjectReductionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IngestionJobService ingestionJobService;

    @InjectMocks
    private BookObjectReductionHandler handler;

    @Test
    @DisplayName("Publishes book objects reduced event when chapter objects are resolved")
    void publishesBookObjectsReducedOnChapterObjectsResolved() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterObjectsResolvedEvent event = new ChapterObjectsResolvedEvent(this, jobId, chapterId, bookId, true, 4, 2);

        when(bookObjectReductionService.resolveBook(bookId))
                .thenReturn(new BookObjectResolutionResult(bookId, true, 2, 1, "ok"));

        handler.handleChapterObjectsResolved(event);

        verify(bookObjectReductionService).resolveBook(bookId);

        ArgumentCaptor<BookObjectsReducedEvent> captor = ArgumentCaptor.forClass(BookObjectsReducedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookObjectsReducedEvent published = captor.getValue();

        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
        assertThat(published.isProcessed()).isTrue();
        assertThat(published.getChapterObjectCount()).isEqualTo(2);
        assertThat(published.getBookObjectCount()).isEqualTo(1);

        verify(eventPublisher).publishEvent(any(BookObjectsReducedEvent.class));
    }

    @Test
    @DisplayName("Publishes retryable failure instead of reduced event when claim is unavailable")
    void publishesRetryableFailureInsteadOfReducedEventWhenClaimUnavailable() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterObjectsResolvedEvent event = new ChapterObjectsResolvedEvent(this, jobId, chapterId, bookId, true, 4, 2);

        when(bookObjectReductionService.resolveBook(bookId))
                .thenThrow(new BookReductionClaimUnavailableException("BOOK_OBJECT_REDUCTION", bookId));

        handler.handleChapterObjectsResolved(event);

        verify(bookObjectReductionService).resolveBook(bookId);
        verify(eventPublisher, never()).publishEvent(any(BookObjectsReducedEvent.class));
        ArgumentCaptor<IngestionFailedEvent> captor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isRetryable()).isTrue();
    }
}
