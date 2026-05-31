package com.lorevault.api.graph.event.consolidation.chapter;

import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterEventAnnRerunService")
class ChapterEventAnnRerunServiceTest {

    @Mock private UniverseGraphRepository universeGraphRepository;
    @Mock private BookGraphRepository bookGraphRepository;
    @Mock private ChapterGraphRepository chapterGraphRepository;
    private ChapterEventAnnRerunService service;

    @BeforeEach
    void setUp() {
        service = new ChapterEventAnnRerunService(
                universeGraphRepository,
                bookGraphRepository,
                chapterGraphRepository
        );
    }

    @Test
    void rerunsSingleChapterWhenChapterIdIsProvided() {
        UUID universeId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Chapter chapter = chapter(chapterId, bookId, universeId, 7);

        when(chapterGraphRepository.findById(chapterId)).thenReturn(java.util.Optional.of(chapter));

        ChapterEventAnnRerunResult result = service.rerun(null, null, chapterId);

        assertThat(result.success()).isTrue();
        assertThat(result.selectedScope().chapterId()).isEqualTo(chapterId);
        assertThat(result.selectedScope().bookId()).isEqualTo(bookId);
        assertThat(result.selectedScope().universeId()).isEqualTo(universeId);
        assertThat(result.triggeredChapterCount()).isOne();
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

        ChapterEventAnnRerunResult result = service.rerun(null, bookId, null);

        assertThat(result.triggeredChapterCount()).isEqualTo(2);
        assertThat(result.selectedScope().bookId()).isEqualTo(bookId);
        assertThat(result.selectedScope().universeId()).isEqualTo(universeId);
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

        ChapterEventAnnRerunResult result = service.rerun(universeId, null, null);

        assertThat(result.triggeredChapterCount()).isEqualTo(2);
        assertThat(result.selectedScope().universeId()).isEqualTo(universeId);
        assertThat(result.selectedScope().bookId()).isNull();
        assertThat(result.selectedScope().chapterId()).isNull();
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
