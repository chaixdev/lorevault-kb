package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.resolution.*;

import com.lorevault.api.content.entities.BookLocation;
import com.lorevault.api.content.entities.BookLocationGraphRepository;
import com.lorevault.api.content.entities.ChapterLocation;
import com.lorevault.api.content.entities.ChapterLocationGraphRepository;
import com.lorevault.api.ingestion.application.result.BookLocationResolutionResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookLocationReductionService")
class BookLocationReductionServiceTest {

    @Mock
    private BookLocationGraphRepository bookLocationRepository;

    @Mock
    private ChapterLocationGraphRepository chapterLocationRepository;

    @InjectMocks
    private BookLocationReductionService service;

    @Test
    @DisplayName("Rebuilds one BookLocation per exact normalized name cluster and alias bridge")
    void rebuildsBookLocationsFromChapterLocations() {
        UUID bookId = UUID.randomUUID();
        UUID chapterAId = UUID.randomUUID();
        UUID chapterBId = UUID.randomUUID();
        UUID chapterCId = UUID.randomUUID();
        UUID rivendellId = UUID.randomUUID();
        UUID lastHomelyHouseId = UUID.randomUUID();
        UUID imladrisId = UUID.randomUUID();
        UUID shireId = UUID.randomUUID();

        ChapterLocation rivendell = chapterLocation(rivendellId, chapterAId, "Rivendell", "rivendell", List.of("Imladris"), 2);
        ChapterLocation lastHomelyHouse = chapterLocation(lastHomelyHouseId, chapterBId, "The Last Homely House", "the last homely house", List.of("Rivendell"), 1);
        ChapterLocation imladris = chapterLocation(imladrisId, chapterCId, "Imladris", "imladris", List.of(), 1);
        ChapterLocation shire = chapterLocation(shireId, chapterAId, "The Shire", "the shire", List.of(), 1);

        when(chapterLocationRepository.findByBookId(bookId)).thenReturn(List.of(lastHomelyHouse, shire, imladris, rivendell));
        when(bookLocationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookLocationRepository.countBookLocationsByBookId(bookId)).thenReturn(2L);

        BookLocationResolutionResult response = service.resolveBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterLocationsProcessed()).isEqualTo(4);
        assertThat(response.bookLocationsCreated()).isEqualTo(2);

        verify(bookLocationRepository).deleteByBookId(bookId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<BookLocation>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(bookLocationRepository).saveAll(savedCaptor.capture());
        List<BookLocation> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(BookLocation::displayName, BookLocation::normalizedName, BookLocation::chapterLocationCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Imladris", "imladris", 3),
                        org.assertj.core.groups.Tuple.tuple("The Shire", "the shire", 1)
                );

        BookLocation mergedCluster = saved.stream()
                .filter(location -> "imladris".equals(location.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(mergedCluster.aliases()).containsExactly("Imladris", "Rivendell");
        assertThat(mergedCluster.representativeChapterLocationId()).isEqualTo(imladrisId);
        assertThat(mergedCluster.firstSeenChapterId()).isEqualTo(chapterCId);

        BookLocation shireCluster = saved.stream()
                .filter(location -> "the shire".equals(location.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(shireCluster.aliases()).isEmpty();
        assertThat(shireCluster.representativeChapterLocationId()).isEqualTo(shireId);
        assertThat(shireCluster.firstSeenChapterId()).isEqualTo(chapterAId);

        for (BookLocation bookLocation : saved) {
            verify(bookLocationRepository).linkBookToLocation(bookId, bookLocation.id());
        }
        verify(bookLocationRepository).linkChapterLocationsToBookLocation(List.of(imladrisId, rivendellId, lastHomelyHouseId), mergedCluster.id());
        verify(bookLocationRepository).linkChapterLocationsToBookLocation(List.of(shireId), shireCluster.id());
    }

    @Test
    @DisplayName("Skips rebuild when no resolvable chapter locations exist")
    void skipsRebuildWhenNoResolvableChapterLocationsExist() {
        UUID bookId = UUID.randomUUID();
        ChapterLocation blank = new ChapterLocation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " ",
                " ",
                List.of(" "),
                1,
                null,
                null
        );

        when(chapterLocationRepository.findByBookId(bookId)).thenReturn(List.of(blank));

        BookLocationResolutionResult response = service.resolveBook(bookId);

        assertThat(response.success()).isFalse();
        assertThat(response.chapterLocationsProcessed()).isZero();
        assertThat(response.bookLocationsCreated()).isZero();

        verify(bookLocationRepository, never()).deleteByBookId(any());
        verify(bookLocationRepository, never()).saveAll(any());
        verify(bookLocationRepository, never()).linkBookToLocation(any(), any());
        verify(bookLocationRepository, never()).linkChapterLocationsToBookLocation(any(), any());
    }

    @Test
    @DisplayName("Returns no-op response when no chapter locations exist for the book")
    void returnsNoOpWhenNoChapterLocationsExist() {
        UUID bookId = UUID.randomUUID();
        when(chapterLocationRepository.findByBookId(bookId)).thenReturn(List.of());

        BookLocationResolutionResult response = service.resolveBook(bookId);

        assertThat(response.success()).isFalse();
        assertThat(response.chapterLocationsProcessed()).isZero();
        assertThat(response.bookLocationsCreated()).isZero();

        verify(bookLocationRepository, never()).deleteByBookId(any());
        verify(bookLocationRepository, never()).saveAll(any());
    }

    private ChapterLocation chapterLocation(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            int mentionCount
    ) {
        return new ChapterLocation(id, chapterId, displayName, normalizedName, aliases, mentionCount, null, null);
    }
}
