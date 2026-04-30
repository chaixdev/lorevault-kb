package com.lorevault.api.ingestion;

import com.lorevault.api.ingestion.resolution.individual.BookIndividualResolutionResult;
import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterIndividualsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualReductionHandler;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualReductionService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookIndividualReductionHandler")
class BookIndividualReductionHandlerTest {

    @Mock
    private BookIndividualReductionService bookIndividualReductionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IngestionJobService ingestionJobService;

    @InjectMocks
    private BookIndividualReductionHandler handler;

    @Test
    @DisplayName("Reduces book individuals automatically when chapter individuals are resolved")
    void reducesBookOnChapterIndividualsResolved() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterIndividualsResolvedEvent event = new ChapterIndividualsResolvedEvent(this, jobId, chapterId, bookId, true, 3, 1);

        when(bookIndividualReductionService.resolveBook(bookId))
                .thenReturn(new BookIndividualResolutionResult(bookId, true, 3, 1, "ok"));

        handler.handleChapterIndividualsResolved(event);

        verify(bookIndividualReductionService).resolveBook(bookId);
        verify(eventPublisher).publishEvent(any(BookIndividualsReducedEvent.class));
    }

    @Test
    @DisplayName("Preserves triggering chapter context when publishing reduced event")
    void publishesReducedEventWithTriggeringChapterContext() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterIndividualsResolvedEvent event = new ChapterIndividualsResolvedEvent(this, jobId, chapterId, bookId, true, 5, 2);

        when(bookIndividualReductionService.resolveBook(bookId))
                .thenReturn(new BookIndividualResolutionResult(bookId, true, 5, 2, "ok"));

        handler.handleChapterIndividualsResolved(event);

        ArgumentCaptor<BookIndividualsReducedEvent> captor = ArgumentCaptor.forClass(BookIndividualsReducedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        BookIndividualsReducedEvent published = captor.getValue();
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
        ChapterIndividualsResolvedEvent event = new ChapterIndividualsResolvedEvent(this, jobId, chapterId, bookId, true, 5, 2);

        when(bookIndividualReductionService.resolveBook(bookId))
                .thenThrow(new BookReductionClaimUnavailableException("BOOK_INDIVIDUAL_REDUCTION", bookId));

        handler.handleChapterIndividualsResolved(event);

        verify(bookIndividualReductionService).resolveBook(bookId);
        verify(eventPublisher, never()).publishEvent(any(BookIndividualsReducedEvent.class));
        ArgumentCaptor<IngestionFailedEvent> captor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isRetryable()).isTrue();
    }
}
