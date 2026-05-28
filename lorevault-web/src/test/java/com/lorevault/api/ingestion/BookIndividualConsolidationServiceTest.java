package com.lorevault.api.ingestion;

import com.lorevault.api.content.association.BookIndividual;
import com.lorevault.api.content.association.BookIndividualGraphRepository;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualPersistenceService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualConsolidationService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualConsolidationResult;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.neo4j.core.Neo4jClient;

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
    private Neo4jClient neo4jClient;

    @Mock
    private Neo4jClient.UnboundRunnableSpec unboundRunnableSpec;

    @Mock
    private Neo4jClient.OngoingBindSpec<String, Neo4jClient.RunnableSpec> ongoingBindSpec;

    @Mock
    private Neo4jClient.RunnableSpec runnableSpec;

    @Mock
    private Neo4jClient.RecordFetchSpec<Map<String, Object>> recordFetchSpec;

    @Mock
    private BookIndividualPersistenceService bookIndividualPersistenceService;

    @InjectMocks
    private BookIndividualConsolidationService service;

    @Test
    @DisplayName("Rebuilds one BookIndividual per normalized name and links chapter individuals")
    void rebuildsBookIndividualsFromCandidates() {
        UUID bookId = UUID.randomUUID();
        UUID nyxChapterIndividualId = UUID.randomUUID();
        UUID orionChapterIndividualId = UUID.randomUUID();

        when(neo4jClient.query(anyString())).thenReturn(unboundRunnableSpec);
        when(unboundRunnableSpec.bind(bookId.toString())).thenReturn(ongoingBindSpec);
        when(ongoingBindSpec.to("bookId")).thenReturn(runnableSpec);
        when(runnableSpec.fetch()).thenReturn(recordFetchSpec);
        when(recordFetchSpec.all()).thenReturn(List.of(
                row(nyxChapterIndividualId, UUID.randomUUID(), "Nyx", "nyx"),
                row(orionChapterIndividualId, UUID.randomUUID(), "Orion", "orion")
        ));
        when(bookIndividualRepository.countChapterIndividualsForBookAndName(bookId, "nyx")).thenReturn(2L);
        when(bookIndividualRepository.countChapterIndividualsForBookAndName(bookId, "orion")).thenReturn(1L);
        when(bookIndividualPersistenceService.countByBookId(bookId)).thenReturn(2L);
        when(bookIndividualPersistenceService.replaceBookIndividuals(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        BookIndividualConsolidationResult response = service.consolidateBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isEqualTo(2);
        assertThat(response.bookIndividualsCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookIndividual>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), savedCaptor.capture());
        List<BookIndividual> saved = savedCaptor.getValue();
        assertThat(saved)
                .extracting(BookIndividual::displayName, BookIndividual::normalizedName, BookIndividual::chapterIndividualCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Nyx", "nyx", 2),
                        org.assertj.core.groups.Tuple.tuple("Orion", "orion", 1)
                );

        verify(bookIndividualRepository, never()).linkChapterIndividualToBookIndividual(any(), any());
    }

    @Test
    @DisplayName("Returns no-op response when no chapter individuals exist for book")
    void noOpWhenNoCandidates() {
        UUID bookId = UUID.randomUUID();
        when(neo4jClient.query(anyString())).thenReturn(unboundRunnableSpec);
        when(unboundRunnableSpec.bind(bookId.toString())).thenReturn(ongoingBindSpec);
        when(ongoingBindSpec.to("bookId")).thenReturn(runnableSpec);
        when(runnableSpec.fetch()).thenReturn(recordFetchSpec);
        when(recordFetchSpec.all()).thenReturn(List.of());

        BookIndividualConsolidationResult response = service.consolidateBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isZero();
        assertThat(response.bookIndividualsCreated()).isZero();
        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), eq(List.of()));
    }

    @Test
    @DisplayName("Retries transient Neo4j lock conflicts at the consolidation boundary")
    void retriesTransientNeo4jLockConflictsAtConsolidationBoundary() throws NoSuchMethodException {
        Retryable retryable = MergedAnnotations.from(BookIndividualConsolidationService.class.getMethod("consolidateBook", UUID.class))
                .get(Retryable.class)
                .synthesize();

        assertThat(retryable.retryFor()).contains(TransientDataAccessException.class, org.neo4j.driver.exceptions.TransientException.class);
        assertThat(retryable.maxAttempts()).isEqualTo(3);
    }

    private Map<String, Object> row(
            UUID chapterIndividualId,
            UUID chapterId,
            String displayName,
            String normalizedName
    ) {
        return Map.of(
                "chapterIndividualId", chapterIndividualId,
                "chapterId", chapterId,
                "displayName", displayName,
                "normalizedName", normalizedName
        );
    }
}
