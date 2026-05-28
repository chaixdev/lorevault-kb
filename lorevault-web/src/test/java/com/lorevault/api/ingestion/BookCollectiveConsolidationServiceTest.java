package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.association.BookCollective;
import com.lorevault.api.content.association.ChapterCollective;
import com.lorevault.api.content.association.ChapterCollectiveGraphRepository;
import com.lorevault.api.ingestion.resolution.collective.BookCollectivePersistenceService;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveConsolidationService;
import com.lorevault.api.ingestion.resolution.collective.BookCollectiveConsolidationResult;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.book.BookGraphRepository;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("BookCollectiveConsolidationService")
class BookCollectiveConsolidationServiceTest {

    @Mock
    private BookGraphRepository bookGraphRepository;

    @Mock
    private ChapterCollectiveGraphRepository chapterCollectiveRepository;

    @Mock
    private BookCollectivePersistenceService bookCollectivePersistenceService;

    @InjectMocks
    private BookCollectiveConsolidationService service;

    @Test
    @DisplayName("Checks whether a book exists before manual reduction")
    void checksWhetherBookExists() {
        UUID bookId = UUID.randomUUID();
        Book book = new Book();
        book.setId(bookId);
        when(bookGraphRepository.findById(bookId)).thenReturn(Optional.of(book));

        assertThat(service.bookExists(bookId)).isTrue();
        assertThat(service.bookExists(null)).isFalse();
    }

    @Test
    @DisplayName("Rebuilds book collectives by normalized name only")
    void rebuildsBookCollectivesByNormalizedNameOnly() {
        UUID bookId = UUID.randomUUID();
        UUID chapterAId = UUID.randomUUID();
        UUID chapterBId = UUID.randomUUID();
        UUID bridgeAId = UUID.randomUUID();
        UUID bridgeBId = UUID.randomUUID();
        UUID councilId = UUID.randomUUID();

        ChapterCollective bridgeA = chapterCollective(
                bridgeAId,
                chapterAId,
                "Bridge Four",
                "bridge four",
                List.of("Bridge Four"),
                "military",
                "Explicit",
                "Bridge Four forms up",
                2
        );
        ChapterCollective bridgeB = chapterCollective(
                bridgeBId,
                chapterBId,
                "The Fourth Bridge Crew",
                "bridge four",
                List.of("Fourth Bridge"),
                null,
                null,
                "They rally together",
                1
        );
        ChapterCollective council = chapterCollective(
                councilId,
                chapterAId,
                "Kholin Council",
                "kholin council",
                List.of("High Council"),
                "government",
                "StronglyImplied",
                "Council convenes",
                1
        );

        when(chapterCollectiveRepository.findByBookId(bookId)).thenReturn(List.of(council, bridgeB, bridgeA));
        when(bookCollectivePersistenceService.replaceBookCollectives(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(bookCollectivePersistenceService.countByBookId(bookId)).thenReturn(2L);

        BookCollectiveConsolidationResult response = service.consolidateBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterCollectivesProcessed()).isEqualTo(3);
        assertThat(response.bookCollectivesCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookCollective>> savedCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<UUID>>> linkedIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookCollectivePersistenceService).replaceBookCollectives(
                eq(bookId),
                savedCaptor.capture(),
                linkedIdsCaptor.capture()
        );

        List<BookCollective> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(BookCollective::displayName, BookCollective::normalizedName, BookCollective::chapterCollectiveCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Bridge Four", "bridge four", 2),
                        org.assertj.core.groups.Tuple.tuple("Kholin Council", "kholin council", 1)
                );

        BookCollective bridgeFour = saved.stream()
                .filter(collective -> "bridge four".equals(collective.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(bridgeFour.aliases()).containsExactly("Bridge Four", "Fourth Bridge");
        assertThat(bridgeFour.collectiveType()).isEqualTo("military");
        assertThat(bridgeFour.certainty()).isEqualTo("Explicit");
        assertThat(bridgeFour.evidence()).isEqualTo("Bridge Four forms up");
        assertThat(bridgeFour.representativeChapterCollectiveId()).isEqualTo(bridgeAId);
        assertThat(bridgeFour.firstSeenChapterId()).isEqualTo(chapterAId);

        assertThat(linkedIdsCaptor.getValue()).satisfiesExactly(
                ids -> assertThat(ids).containsExactlyInAnyOrder(bridgeAId, bridgeBId),
                ids -> assertThat(ids).containsExactly(councilId)
        );
    }

    @Test
    @DisplayName("Returns successful zero-count result when no chapter collectives exist for the book")
    void returnsSuccessfulNoOpWhenNoChapterCollectivesExist() {
        UUID bookId = UUID.randomUUID();
        when(chapterCollectiveRepository.findByBookId(bookId)).thenReturn(List.of());

        BookCollectiveConsolidationResult response = service.consolidateBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterCollectivesProcessed()).isZero();
        assertThat(response.bookCollectivesCreated()).isZero();

        verify(bookCollectivePersistenceService).replaceBookCollectives(eq(bookId), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("Retries transient Neo4j lock conflicts at the consolidation boundary")
    void retriesTransientNeo4jLockConflictsAtConsolidationBoundary() throws NoSuchMethodException {
        Retryable retryable = MergedAnnotations.from(BookCollectiveConsolidationService.class.getMethod("consolidateBook", UUID.class))
                .get(Retryable.class)
                .synthesize();

        assertThat(retryable.retryFor()).contains(TransientDataAccessException.class, org.neo4j.driver.exceptions.TransientException.class);
        assertThat(retryable.maxAttempts()).isEqualTo(3);
    }

    private ChapterCollective chapterCollective(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            String collectiveType,
            String certainty,
            String evidence,
            int mentionCount
    ) {
        return new ChapterCollective(
                id,
                chapterId,
                displayName,
                normalizedName,
                aliases,
                collectiveType,
                certainty,
                evidence,
                mentionCount,
                null,
                null
        );
    }
}
