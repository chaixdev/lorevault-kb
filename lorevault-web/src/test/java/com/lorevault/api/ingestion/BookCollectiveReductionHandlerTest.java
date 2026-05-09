package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.ingestion.events.BookCollectivesReducedEvent;
import com.lorevault.api.ingestion.events.ChapterCollectivesResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveReductionHandler;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveReductionService;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveResolutionResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookCollectiveReductionHandler")
class BookCollectiveReductionHandlerTest {

    @Mock
    private BookCollectiveReductionService bookCollectiveReductionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IngestionJobService ingestionJobService;

    @Mock
    private BookReductionClaimService bookReductionClaimService;

    @InjectMocks
    private BookCollectiveReductionHandler handler;

    @BeforeEach
    void setUp() {
        when(bookReductionClaimService.tryAcquireClaim(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Publishes book collectives reduced event when chapter collectives are resolved")
    void publishesBookCollectivesReducedOnChapterCollectivesResolved() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterCollectivesResolvedEvent event = new ChapterCollectivesResolvedEvent(this, jobId, chapterId, bookId, true, 4, 2);

        when(bookCollectiveReductionService.resolveBook(bookId))
                .thenReturn(new BookCollectiveResolutionResult(bookId, true, 2, 1, "ok"));

        handler.handleChapterCollectivesResolved(event);

        verify(bookCollectiveReductionService).resolveBook(bookId);

        ArgumentCaptor<BookCollectivesReducedEvent> captor = ArgumentCaptor.forClass(BookCollectivesReducedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookCollectivesReducedEvent published = captor.getValue();

        assertThat(published.getJobId()).isEqualTo(jobId);
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
        assertThat(published.isProcessed()).isTrue();
        assertThat(published.getChapterCollectiveCount()).isEqualTo(2);
        assertThat(published.getBookCollectiveCount()).isEqualTo(1);

        verify(eventPublisher).publishEvent(any(BookCollectivesReducedEvent.class));
    }

    @Test
    @DisplayName("Publishes retryable failure instead of reduced event when claim is unavailable")
    void publishesRetryableFailureInsteadOfReducedEventWhenClaimUnavailable() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ChapterCollectivesResolvedEvent event = new ChapterCollectivesResolvedEvent(this, jobId, chapterId, bookId, true, 4, 2);

        when(bookCollectiveReductionService.resolveBook(bookId))
                .thenThrow(new BookReductionClaimUnavailableException("BOOK_COLLECTIVE_REDUCTION", bookId));

        handler.handleChapterCollectivesResolved(event);

        verify(bookCollectiveReductionService).resolveBook(bookId);
        verify(eventPublisher, never()).publishEvent(any(BookCollectivesReducedEvent.class));
        ArgumentCaptor<IngestionFailedEvent> captor = ArgumentCaptor.forClass(IngestionFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isRetryable()).isTrue();
    }
}
