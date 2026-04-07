package com.lorevault.api.service.library;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.BookGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SeriesGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.UniverseGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryQueryServiceTest {

    @Mock
    private UniverseGraphRepository universeRepo;

    @Mock
    private SeriesGraphRepository seriesRepo;

    @Mock
    private BookGraphRepository bookRepo;

    @Mock
    private ChapterGraphRepository chapterGraphRepository;

    private LibraryQueryService service;

    @BeforeEach
    void setUp() {
        service = new LibraryQueryService(universeRepo, seriesRepo, bookRepo, chapterGraphRepository);
    }

    @Test
    void listUniversesSortsByNameCaseInsensitive() {
        Universe cosmos = new Universe(UUID.randomUUID(), "Cosmos", "cosmos", LocalDateTime.now(), LocalDateTime.now());
        Universe aether = new Universe(UUID.randomUUID(), "aether", "aether", LocalDateTime.now(), LocalDateTime.now());
        Universe zeta = new Universe(UUID.randomUUID(), "Zeta", "zeta", LocalDateTime.now(), LocalDateTime.now());

        when(universeRepo.findAll()).thenReturn(List.of(zeta, cosmos, aether));

    List<LibraryQueryService.UniverseSummary> universes = service.listUniverses();

    assertThat(universes)
        .extracting(LibraryQueryService.UniverseSummary::name)
                .containsExactly("aether", "Cosmos", "Zeta");
    }

    @Test
    void listBooksForUniverseSortsBySeriesNumberAndTitle() {
        UUID universeId = UUID.randomUUID();
        Book standalone = new Book(UUID.randomUUID(), universeId, null, "Cosmos", null, null, "Guide", LocalDateTime.now(), LocalDateTime.now());
        Book seriesSecond = new Book(UUID.randomUUID(), universeId, UUID.randomUUID(), "Cosmos", "Chronicles", 2, "Echoes", LocalDateTime.now(), LocalDateTime.now());
        Book seriesFirst = new Book(UUID.randomUUID(), universeId, seriesSecond.getSeriesId(), "Cosmos", "Chronicles", 1, "Awakening", LocalDateTime.now(), LocalDateTime.now());
        Book anotherSeries = new Book(UUID.randomUUID(), universeId, UUID.randomUUID(), "Cosmos", "Aetherbound", 1, "Arrival", LocalDateTime.now(), LocalDateTime.now());

        when(bookRepo.findByUniverseId(universeId)).thenReturn(List.of(seriesSecond, standalone, seriesFirst, anotherSeries));

    List<LibraryQueryService.BookSummary> books = service.listBooksForUniverse(universeId);

    assertThat(books)
        .extracting(LibraryQueryService.BookSummary::displayLabel)
                .containsExactly(
                        "Aetherbound #1 Arrival",
                        "Chronicles #1 Awakening",
                        "Chronicles #2 Echoes",
                        "Guide"
                );
    }

    @Test
    void listSeriesFiltersByUniverse() {
        UUID universeId = UUID.randomUUID();
        Series alpha = new Series(UUID.randomUUID(), universeId, "Cosmos", "Alpha", LocalDateTime.now(), LocalDateTime.now());
        Series omega = new Series(UUID.randomUUID(), universeId, "Cosmos", "omega", LocalDateTime.now(), LocalDateTime.now());

        when(seriesRepo.findByUniverseId(universeId)).thenReturn(List.of(omega, alpha));

    List<LibraryQueryService.SeriesSummary> series = service.listSeries(universeId);

    assertThat(series)
        .extracting(LibraryQueryService.SeriesSummary::name)
                .containsExactly("Alpha", "omega");
    }
}
