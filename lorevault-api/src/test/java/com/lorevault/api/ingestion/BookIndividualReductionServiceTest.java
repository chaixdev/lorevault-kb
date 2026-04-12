package com.lorevault.api.ingestion;

import com.lorevault.api.content.BookIndividual;
import com.lorevault.api.content.BookIndividualGraphRepository;
import com.lorevault.api.support.BookIndividualResolutionResponse;
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
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private BookIndividualReductionService service;

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
        when(bookIndividualRepository.countBookIndividualsByBookId(bookId)).thenReturn(2L);
        when(bookIndividualRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BookIndividualResolutionResponse response = service.resolveBook(bookId);

        assertThat(response.isProcessed()).isTrue();
        assertThat(response.getChapterIndividualCount()).isEqualTo(2);
        assertThat(response.getBookIndividualCount()).isEqualTo(2);

        verify(bookIndividualRepository).deleteByBookId(bookId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<BookIndividual>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(bookIndividualRepository).saveAll(savedCaptor.capture());
        List<BookIndividual> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());
        assertThat(saved)
                .extracting(BookIndividual::displayName, BookIndividual::normalizedName, BookIndividual::chapterIndividualCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Nyx", "nyx", 2),
                        org.assertj.core.groups.Tuple.tuple("Orion", "orion", 1)
                );

        for (BookIndividual bookIndividual : saved) {
            verify(bookIndividualRepository).linkBookToIndividual(bookId, bookIndividual.id());
        }
        verify(bookIndividualRepository).linkChapterIndividualToBookIndividual(eq(nyxChapterIndividualId), any());
        verify(bookIndividualRepository).linkChapterIndividualToBookIndividual(eq(orionChapterIndividualId), any());
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

        BookIndividualResolutionResponse response = service.resolveBook(bookId);

        assertThat(response.isProcessed()).isFalse();
        assertThat(response.getChapterIndividualCount()).isZero();
        assertThat(response.getBookIndividualCount()).isZero();
        verify(bookIndividualRepository, never()).deleteByBookId(any());
        verify(bookIndividualRepository, never()).saveAll(any());
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
