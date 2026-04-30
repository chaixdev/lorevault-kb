package com.lorevault.api.ingestion;

import com.lorevault.api.content.association.BookIndividual;
import com.lorevault.api.content.association.BookIndividualGraphRepository;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimUnavailableException;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualPersistenceService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualReductionService;
import com.lorevault.api.ingestion.resolution.location.BookReductionClaimService;
import com.lorevault.api.ingestion.resolution.individual.BookIndividualResolutionResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookIndividualReductionService")
class BookIndividualReductionServiceTest {

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
    private BookReductionClaimService claimService;

    @Mock
    private BookIndividualPersistenceService bookIndividualPersistenceService;

    @InjectMocks
    private BookIndividualReductionService service;

    @BeforeEach
    void setUp() {
        when(claimService.tryAcquireClaimWithRetry(any(), anyInt(), anyLong())).thenReturn(true);
    }

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

        BookIndividualResolutionResult response = service.resolveBook(bookId);

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

        BookIndividualResolutionResult response = service.resolveBook(bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterIndividualsProcessed()).isZero();
        assertThat(response.bookIndividualsCreated()).isZero();
        verify(bookIndividualPersistenceService).replaceBookIndividuals(eq(bookId), eq(List.of()));
    }

    @Test
    @DisplayName("Throws typed claim exception when book reduction claim cannot be acquired")
    void throwsTypedClaimExceptionWhenClaimCannotBeAcquired() {
        UUID bookId = UUID.randomUUID();
        when(claimService.tryAcquireClaimWithRetry(bookId, 6, 500)).thenReturn(false);

        assertThatThrownBy(() -> service.resolveBook(bookId))
                .isInstanceOf(BookReductionClaimUnavailableException.class)
                .hasMessageContaining("BOOK_INDIVIDUAL_REDUCTION")
                .hasMessageContaining(bookId.toString());

        verify(neo4jClient, never()).query(anyString());
        verify(bookIndividualPersistenceService, never()).replaceBookIndividuals(any(), any());
        verify(claimService, never()).releaseClaim(any());
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
