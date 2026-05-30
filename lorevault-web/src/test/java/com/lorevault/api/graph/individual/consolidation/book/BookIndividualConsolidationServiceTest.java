package com.lorevault.api.graph.individual.consolidation.book;

import com.lorevault.api.graph.individual.persistence.BookIndividual;
import com.lorevault.api.graph.individual.persistence.BookIndividualGraphRepository;
import com.lorevault.api.graph.individual.persistence.ChapterIndividual;
import com.lorevault.api.graph.individual.persistence.ChapterIndividualGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.library.book.BookGraphRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Retryable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookIndividualConsolidationService")
class BookIndividualConsolidationServiceTest {

    @Mock
    private BookIndividualGraphRepository bookIndividualRepository;

    @Mock
    private BookGraphRepository bookGraphRepository;

    @Mock
    private ChapterIndividualGraphRepository chapterIndividualRepository;

    @Mock
    private BookIndividualPersistenceService bookIndividualPersistenceService;

    @Spy
    private ConsolidationEngine consolidationEngine = new ConsolidationEngine();

    @InjectMocks
    private BookIndividualConsolidationService service;

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.BOOK_INDIVIDUAL_CONSOLIDATION);

    @Test
    @DisplayName("Rebuilds one BookIndividual per cluster and links chapter individuals by ID")
    void rebuildsBookIndividualsFromClusters() {
        UUID bookId = UUID.randomUUID();
        UUID nyxCiId1 = UUID.randomUUID();
        UUID nyxCiId2 = UUID.randomUUID();
        UUID orionCiId = UUID.randomUUID();
        UUID chapter1Id = UUID.randomUUID();
        UUID chapter2Id = UUID.randomUUID();

        ChapterIndividual nyxCi1 = new ChapterIndividual(nyxCiId1, chapter1Id, UUID.randomUUID(),
                "Nyx", "nyx", List.of("nyx", "night walker"), 2, null, null);
        ChapterIndividual nyxCi2 = new ChapterIndividual(nyxCiId2, chapter2Id, UUID.randomUUID(),
                "Nyx", "nyx", List.of("nyx"), 1, null, null);
        ChapterIndividual orionCi = new ChapterIndividual(orionCiId, chapter1Id, UUID.randomUUID(),
                "Orion", "orion", List.of("orion"), 1, null, null);

        when(chapterIndividualRepository.findByBookId(bookId)).thenReturn(List.of(nyxCi1, nyxCi2, orionCi));
        when(bookIndividualPersistenceService.countByBookId(bookId)).thenReturn(2L);

        BookIndividualConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isEqualTo(3);
        assertThat(response.bookIndividualsCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookIndividual>> savedCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<UUID>>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), savedCaptor.capture(), idsCaptor.capture());

        List<BookIndividual> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(BookIndividual::normalizedName).containsExactlyInAnyOrder("nyx", "orion");

        List<List<UUID>> idsByBi = idsCaptor.getValue();
        assertThat(idsByBi).hasSize(2);
    }

    @Test
    @DisplayName("Returns no-op response when no chapter individuals exist for book")
    void noOpWhenNoCandidates() {
        UUID bookId = UUID.randomUUID();
        when(chapterIndividualRepository.findByBookId(bookId)).thenReturn(List.of());

        BookIndividualConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isZero();
        assertThat(response.bookIndividualsCreated()).isZero();
        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("Retries transient Neo4j lock conflicts at the consolidation boundary")
    void retriesTransientNeo4jLockConflictsAtConsolidationBoundary() throws NoSuchMethodException {
        Retryable retryable = MergedAnnotations.from(BookIndividualConsolidationService.class.getMethod("consolidateBook", StageExecutionContext.class, UUID.class))
                .get(Retryable.class)
                .synthesize();

        assertThat(retryable.retryFor()).contains(TransientDataAccessException.class, org.neo4j.driver.exceptions.TransientException.class);
        assertThat(retryable.maxAttempts()).isEqualTo(3);
    }
}