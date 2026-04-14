package com.lorevault.api.ingestion;

import com.lorevault.api.support.BookLocationResolutionResponse;
import java.util.UUID;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookLocationReductionHandler")
class BookLocationReductionHandlerTest {

    @Mock
    private BookLocationReductionService bookLocationReductionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
                .thenReturn(new BookLocationResolutionResponse(bookId, true, 2, 1, "ok"));

        handler.handleChapterLocationsResolved(event);

        verify(bookLocationReductionService).resolveBook(bookId);
        verify(eventPublisher).publishEvent(any(BookLocationsReducedEvent.class));

        ArgumentCaptor<BookLocationsReducedEvent> captor = ArgumentCaptor.forClass(BookLocationsReducedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookLocationsReducedEvent published = captor.getValue();
        assertThat(new org.springframework.beans.BeanWrapperImpl(published).getPropertyValue("jobId")).isEqualTo(jobId);
        assertThat(new org.springframework.beans.BeanWrapperImpl(published).getPropertyValue("chapterId")).isEqualTo(chapterId);
        assertThat(new org.springframework.beans.BeanWrapperImpl(published).getPropertyValue("bookId")).isEqualTo(bookId);
    }
}
