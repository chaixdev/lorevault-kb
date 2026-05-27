package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.association.BookIndividual;
import com.lorevault.api.content.association.ChapterIndividual;
import com.lorevault.api.content.association.ChapterIndividualGraphRepository;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualPersistenceService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualReductionService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualResolutionResult;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("BookIndividualReductionService")
class BookIndividualReductionServiceTest {

    @Mock
    private ChapterIndividualGraphRepository chapterIndividualRepository;

    @Mock
    private BookIndividualPersistenceService bookIndividualPersistenceService;

    @InjectMocks
    private BookIndividualReductionService service;

    @Test
    @DisplayName("Rebuilds one BookIndividual per cluster and links chapter individuals by ID")
    void rebuildsBookIndividualsFromChapterIndividuals() {
        UUID bookId = UUID.randomUUID();
        UUID chapterAId = UUID.randomUUID();
        UUID chapterBId = UUID.randomUUID();
        UUID chapterCId = UUID.randomUUID();
        UUID nyxAId = UUID.randomUUID();
        UUID nyxBId = UUID.randomUUID();
        UUID orionId = UUID.randomUUID();

        ChapterIndividual nyxA = chapterIndividual(nyxAId, chapterAId, "Nyx", "nyx", List.of("Goddess of Night"), 2);
        ChapterIndividual nyxB = chapterIndividual(nyxBId, chapterBId, "Goddess of Night", "goddess of night", List.of("Nyx"), 1);
        ChapterIndividual orion = chapterIndividual(orionId, chapterCId, "Orion", "orion", List.of(), 1);

        when(chapterIndividualRepository.findByBookId(bookId)).thenReturn(List.of(orion, nyxB, nyxA));
        when(bookIndividualPersistenceService.replaceBookIndividuals(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(bookIndividualPersistenceService.countByBookId(bookId)).thenReturn(2L);

        BookIndividualResolutionResult response = service.resolveBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isEqualTo(3);
        assertThat(response.bookIndividualsCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookIndividual>> savedCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<UUID>>> linkedIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), savedCaptor.capture(), linkedIdsCaptor.capture());

        List<BookIndividual> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(BookIndividual::displayName, BookIndividual::normalizedName, BookIndividual::chapterIndividualCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Goddess of Night", "goddess of night", 2),
                        org.assertj.core.groups.Tuple.tuple("Orion", "orion", 1)
                );

        BookIndividual goddessCluster = saved.stream()
                .filter(bi -> "goddess of night".equals(bi.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(goddessCluster.aliases()).containsExactlyInAnyOrder("Nyx", "Goddess of Night");
        assertThat(goddessCluster.representativeChapterIndividualId()).isEqualTo(nyxBId);
        assertThat(goddessCluster.firstSeenChapterId()).isEqualTo(chapterBId);

        BookIndividual orionCluster = saved.stream()
                .filter(bi -> "orion".equals(bi.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(orionCluster.aliases()).isEmpty();
        assertThat(orionCluster.representativeChapterIndividualId()).isEqualTo(orionId);
        assertThat(orionCluster.firstSeenChapterId()).isEqualTo(chapterCId);

        assertThat(linkedIdsCaptor.getValue()).satisfiesExactly(
                ids -> assertThat(ids).containsExactlyInAnyOrder(nyxBId, nyxAId),
                ids -> assertThat(ids).containsExactly(orionId)
        );
    }

    @Test
    @DisplayName("Returns successful no-op response when no chapter individuals exist for book")
    void returnsSuccessfulNoOpWhenNoChapterIndividualsExist() {
        UUID bookId = UUID.randomUUID();
        when(chapterIndividualRepository.findByBookId(bookId)).thenReturn(List.of());

        BookIndividualResolutionResult response = service.resolveBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isZero();
        assertThat(response.bookIndividualsCreated()).isZero();

        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("Retries transient Neo4j lock conflicts at the reducer boundary")
    void retriesTransientNeo4jLockConflictsAtReducerBoundary() throws NoSuchMethodException {
        Retryable retryable = MergedAnnotations.from(BookIndividualReductionService.class.getMethod("resolveBook", UUID.class))
                .get(Retryable.class)
                .synthesize();

        assertThat(retryable.retryFor()).contains(TransientDataAccessException.class, org.neo4j.driver.exceptions.TransientException.class);
        assertThat(retryable.maxAttempts()).isEqualTo(3);
    }

    private ChapterIndividual chapterIndividual(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            int mentionCount
    ) {
        return new ChapterIndividual(id, chapterId, displayName, normalizedName, aliases, mentionCount, null, null);
    }
}
