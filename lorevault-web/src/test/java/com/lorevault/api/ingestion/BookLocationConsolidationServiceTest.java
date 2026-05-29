package com.lorevault.api.ingestion;

import com.lorevault.api.content.association.BookLocation;
import com.lorevault.api.content.association.ChapterLocation;
import com.lorevault.api.content.association.ChapterLocationGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.resolution.location.BookLocationPersistenceService;
import com.lorevault.api.ingestion.resolution.location.BookLocationConsolidationService;
import com.lorevault.api.ingestion.resolution.location.BookLocationConsolidationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Retryable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookLocationConsolidationService")
class BookLocationConsolidationServiceTest {

    @Mock
    private ChapterLocationGraphRepository chapterLocationRepository;

    @Mock
    private BookLocationPersistenceService bookLocationPersistenceService;

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.BOOK_LOCATION_CONSOLIDATION);

    @InjectMocks
    private BookLocationConsolidationService service;

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
        when(bookLocationPersistenceService.replaceBookLocations(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(bookLocationPersistenceService.countByBookId(bookId)).thenReturn(2L);

        BookLocationConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterLocationsProcessed()).isEqualTo(4);
        assertThat(response.bookLocationsCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookLocation>> savedCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<UUID>>> linkedIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookLocationPersistenceService).replaceBookLocations(eq(bookId), savedCaptor.capture(), linkedIdsCaptor.capture());
        List<BookLocation> saved = savedCaptor.getValue();

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

        assertThat(linkedIdsCaptor.getValue())
                .containsExactly(
                        List.of(imladrisId, rivendellId, lastHomelyHouseId),
                        List.of(shireId)
                );
    }

    @Test
    @DisplayName("Skips rebuild when no resolvable chapter locations exist")
    void skipsRebuildWhenNoResolvableChapterLocationsExist() {
        UUID bookId = UUID.randomUUID();
        ChapterLocation blank = new ChapterLocation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                " ",
                " ",
                List.of(" "),
                1,
                null,
                null
        );

        when(chapterLocationRepository.findByBookId(bookId)).thenReturn(List.of(blank));

        BookLocationConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterLocationsProcessed()).isZero();
        assertThat(response.bookLocationsCreated()).isZero();

        verify(bookLocationPersistenceService).replaceBookLocations(eq(bookId), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("Returns no-op response when no chapter locations exist for the book")
    void returnsNoOpWhenNoChapterLocationsExist() {
        UUID bookId = UUID.randomUUID();
        when(chapterLocationRepository.findByBookId(bookId)).thenReturn(List.of());

        BookLocationConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterLocationsProcessed()).isZero();
        assertThat(response.bookLocationsCreated()).isZero();

        verify(bookLocationPersistenceService).replaceBookLocations(eq(bookId), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("Retries transient Neo4j lock conflicts at the consolidation boundary")
    void retriesTransientNeo4jLockConflictsAtConsolidationBoundary() throws NoSuchMethodException {
        Retryable retryable = MergedAnnotations.from(BookLocationConsolidationService.class.getMethod("consolidateBook", StageExecutionContext.class, UUID.class))
                .get(Retryable.class)
                .synthesize();

        assertThat(retryable.retryFor()).contains(TransientDataAccessException.class, org.neo4j.driver.exceptions.TransientException.class);
        assertThat(retryable.maxAttempts()).isEqualTo(3);
    }

    private ChapterLocation chapterLocation(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            int mentionCount
    ) {
        return new ChapterLocation(id, chapterId, null, displayName, normalizedName, aliases, mentionCount, null, null);
    }
}
