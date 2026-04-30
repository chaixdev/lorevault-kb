package com.lorevault.api.ingestion;

import com.lorevault.api.ingestion.resolution.location.BookLocationResolutionResult;
import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterLocationsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import java.util.UUID;

import com.lorevault.api.ingestion.resolution.location.BookLocationReductionHandler;
import com.lorevault.api.ingestion.resolution.location.BookLocationReductionService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookLocationReductionHandler")
class BookLocationReductionHandlerTest {

    @Mock
    private BookLocationReductionService bookLocationReductionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IngestionJobService ingestionJobService;

    @InjectMocks
    private BookLocationReductionHandler handler;

    @Test
    @DisplayName("Reduces book locations automatically when chapter locations are resolved")
    void reducesBookLocationsOnChapterLocationsResolved() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterLocationsResolvedEvent event = new ChapterLocationsResolvedEvent(this, jobId, chapterId, bookId, true, 4, 2);

        when(bookLocationReductionService.resolveBook(bookId))
                .thenReturn(new BookLocationResolutionResult(bookId, true, 2, 1, "ok"));

        handler.handleChapterLocationsResolved(event);

        verify(bookLocationReductionService).resolveBook(bookId);
        verify(eventPublisher).publishEvent(any(BookLocationsReducedEvent.class));

        ArgumentCaptor<BookLocationsReducedEvent> captor = ArgumentCaptor.forClass(BookLocationsReducedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookLocationsReducedEvent published = captor.getValue();
        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
    }

    @Test
    @DisplayName("Publishes retryable failure instead of reduced event when claim is unavailable")
    void publishesRetryableFailureInsteadOfReducedEventWhenClaimUnavailable() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterLocationsResolvedEvent event = new ChapterLocationsResolvedEvent(this, jobId, chapterId, bookId, true, 4, 2);

        when(bookLocationReductionService.resolveBook(bookId))
                .thenThrow(new BookReductionClaimUnavailableException("BOOK_LOCATION_REDUCTION", bookId));

        handler.handleChapterLocationsResolved(event);

        verify(bookLocationReductionService).resolveBook(bookId);
        verify(eventPublisher, never()).publishEvent(any(BookLocationsReducedEvent.class));
        ArgumentCaptor<IngestionFailedEvent> captor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isRetryable()).isTrue();
    }
}
