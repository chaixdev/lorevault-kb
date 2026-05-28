package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.association.ChapterEventGraphRepository;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.ingestion.events.ChapterEventsConsolidatedEvent;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.library.universe.Universe;
import com.lorevault.api.library.universe.UniverseGraphRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterEventAnnRerunService")
class ChapterEventAnnRerunServiceTest {

    @Mock private UniverseGraphRepository universeGraphRepository;
    @Mock private BookGraphRepository bookGraphRepository;
    @Mock private ChapterGraphRepository chapterGraphRepository;
    @Mock private ChapterEventGraphRepository chapterEventGraphRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChapterEventAnnRerunService service;

    @BeforeEach
    void setUp() {
        service = new ChapterEventAnnRerunService(
                universeGraphRepository,
                bookGraphRepository,
                chapterGraphRepository,
                chapterEventGraphRepository,
                eventPublisher
        );
    }

    @Test
    void rerunsSingleChapterWhenChapterIdIsProvided() {
        UUID universeId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Chapter chapter = chapter(chapterId, bookId, universeId, 7);

        when(chapterGraphRepository.findById(chapterId)).thenReturn(java.util.Optional.of(chapter));
        when(chapterEventGraphRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(chapterEventGraphRepository.countChapterEventsByChapterId(chapterId)).thenReturn(2L);

        ChapterEventAnnRerunResult result = service.rerun(null, null, chapterId);

        assertThat(result.success()).isTrue();
        assertThat(result.selectedScope().chapterId()).isEqualTo(chapterId);
        assertThat(result.selectedScope().bookId()).isEqualTo(bookId);
        assertThat(result.selectedScope().universeId()).isEqualTo(universeId);
        assertThat(result.triggeredChapterCount()).isOne();

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ChapterEventsConsolidatedEvent published = (ChapterEventsConsolidatedEvent) captor.getValue();
        assertThat(published.getChapterId()).isEqualTo(chapterId);
        assertThat(published.getBookId()).isEqualTo(bookId);
        assertThat(published.getMentionCount()).isEqualTo(3);
        assertThat(published.getChapterEventCount()).isEqualTo(2);
        assertThat(published.getFailedCorefWindowCount()).isZero();
    }

    @Test
    void rerunsAllChaptersInBookWhenBookIdIsProvided() {
        UUID universeId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID chapterOneId = UUID.randomUUID();
        UUID chapterTwoId = UUID.randomUUID();

        Book book = book(bookId, universeId);
        Chapter chapterOne = chapter(chapterOneId, bookId, universeId, 1);
        Chapter chapterTwo = chapter(chapterTwoId, bookId, universeId, 2);

        when(bookGraphRepository.findById(bookId)).thenReturn(java.util.Optional.of(book));
        when(chapterGraphRepository.findByBookId(bookId)).thenReturn(List.of(chapterOne, chapterTwo));
        when(chapterEventGraphRepository.countMentionsByChapterId(chapterOneId)).thenReturn(4L);
        when(chapterEventGraphRepository.countChapterEventsByChapterId(chapterOneId)).thenReturn(3L);
        when(chapterEventGraphRepository.countMentionsByChapterId(chapterTwoId)).thenReturn(5L);
        when(chapterEventGraphRepository.countChapterEventsByChapterId(chapterTwoId)).thenReturn(4L);

        ChapterEventAnnRerunResult result = service.rerun(null, bookId, null);

        assertThat(result.triggeredChapterCount()).isEqualTo(2);
        assertThat(result.selectedScope().bookId()).isEqualTo(bookId);
        assertThat(result.selectedScope().universeId()).isEqualTo(universeId);

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        List<ChapterEventsConsolidatedEvent> published = captor.getAllValues().stream()
                .map(ChapterEventsConsolidatedEvent.class::cast)
                .toList();
        assertThat(published).extracting(ChapterEventsConsolidatedEvent::getChapterId)
                .containsExactly(chapterOneId, chapterTwoId);
    }

    @Test
    void rerunsAllChaptersInUniverseWhenUniverseIdIsProvided() {
        UUID universeId = UUID.randomUUID();
        UUID bookOneId = UUID.randomUUID();
        UUID bookTwoId = UUID.randomUUID();
        UUID chapterOneId = UUID.randomUUID();
        UUID chapterTwoId = UUID.randomUUID();

        Universe universe = universe(universeId);
        Book bookOne = book(bookOneId, universeId);
        Book bookTwo = book(bookTwoId, universeId);
        Chapter chapterOne = chapter(chapterOneId, bookOneId, universeId, 1);
        Chapter chapterTwo = chapter(chapterTwoId, bookTwoId, universeId, 1);

        when(universeGraphRepository.findById(universeId)).thenReturn(java.util.Optional.of(universe));
        when(bookGraphRepository.findByUniverseId(universeId)).thenReturn(List.of(bookOne, bookTwo));
        when(chapterGraphRepository.findByBookId(bookOneId)).thenReturn(List.of(chapterOne));
        when(chapterGraphRepository.findByBookId(bookTwoId)).thenReturn(List.of(chapterTwo));
        when(chapterEventGraphRepository.countMentionsByChapterId(chapterOneId)).thenReturn(1L);
        when(chapterEventGraphRepository.countChapterEventsByChapterId(chapterOneId)).thenReturn(1L);
        when(chapterEventGraphRepository.countMentionsByChapterId(chapterTwoId)).thenReturn(2L);
        when(chapterEventGraphRepository.countChapterEventsByChapterId(chapterTwoId)).thenReturn(2L);

        ChapterEventAnnRerunResult result = service.rerun(universeId, null, null);

        assertThat(result.triggeredChapterCount()).isEqualTo(2);
        assertThat(result.selectedScope().universeId()).isEqualTo(universeId);
        assertThat(result.selectedScope().bookId()).isNull();
        assertThat(result.selectedScope().chapterId()).isNull();

        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingUniverseForDefaultScope() {
        assertThatThrownBy(() -> service.rerun(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("universeId is required");
    }

    private Chapter chapter(UUID chapterId, UUID bookId, UUID universeId, int chapterNumber) {
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setBookId(bookId);
        chapter.setUniverseId(universeId);
        chapter.setChapterNumber(chapterNumber);
        return chapter;
    }

    private Book book(UUID bookId, UUID universeId) {
        Book book = new Book();
        book.setId(bookId);
        book.setUniverseId(universeId);
        return book;
    }

    private Universe universe(UUID universeId) {
        Universe universe = new Universe();
        universe.setId(universeId);
        return universe;
    }
}
