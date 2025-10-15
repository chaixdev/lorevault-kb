package com.lorevault.api.service.library;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Read-side service providing sorted library hierarchy data for the UI layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryQueryService {

    private final ContentPersistencePort contentPersistencePort;

    public List<UniverseSummary> listUniverses() {
        List<UniverseSummary> universes = contentPersistencePort.findAllUniverses().stream()
                .sorted(Comparator.comparing(Universe::getName, String.CASE_INSENSITIVE_ORDER))
                .map(universe -> new UniverseSummary(universe.getId(), universe.getName()))
                .toList();
        log.debug("Loaded {} universes for UI", universes.size());
        return universes;
    }

    public List<SeriesSummary> listSeries(UUID universeId) {
        if (universeId == null) {
            return List.of();
        }
        List<SeriesSummary> series = contentPersistencePort.findSeriesByUniverseId(universeId).stream()
                .sorted(Comparator.comparing(Series::getName, String.CASE_INSENSITIVE_ORDER))
                .map(item -> new SeriesSummary(item.getId(), item.getUniverseId(), item.getName()))
                .toList();
        log.debug("Loaded {} series for universe {}", series.size(), universeId);
        return series;
    }

    public List<BookSummary> listBooksForUniverse(UUID universeId) {
        if (universeId == null) {
            return List.of();
        }
        List<BookSummary> books = contentPersistencePort.findBooksByUniverseId(universeId).stream()
                .sorted(bookComparator())
                .map(BookSummary::from)
                .toList();
        log.debug("Loaded {} books for universe {}", books.size(), universeId);
        return books;
    }

    public List<BookSummary> listBooksForSeries(UUID seriesId) {
        if (seriesId == null) {
            return List.of();
        }
        List<BookSummary> books = contentPersistencePort.findBooksBySeriesId(seriesId).stream()
                .sorted(bookComparator())
                .map(BookSummary::from)
                .toList();
        log.debug("Loaded {} books for series {}", books.size(), seriesId);
        return books;
    }

    private Comparator<Book> bookComparator() {
        return Comparator
                .comparing((Book b) -> b.getSeries() == null || b.getSeries().isBlank())
                .thenComparing(book -> book.getSeries() == null ? "" : book.getSeries(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(book -> book.getBookNumber() == null ? Integer.MAX_VALUE : book.getBookNumber())
                .thenComparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER);
    }

    public record UniverseSummary(UUID id, String name) { }

    public record SeriesSummary(UUID id, UUID universeId, String name) { }

    public record BookSummary(UUID id,
                              UUID universeId,
                              UUID seriesId,
                              String universeName,
                              String seriesName,
                              Integer bookNumber,
                              String title) {

        public static BookSummary from(Book book) {
            return new BookSummary(
                    book.getId(),
                    book.getUniverseId(),
                    book.getSeriesId(),
                    book.getUniverse(),
                    book.getSeries(),
                    book.getBookNumber(),
                    book.getTitle()
            );
        }

        public String displayLabel() {
            String seriesPart = (seriesName == null || seriesName.isBlank()) ? "" : (seriesName + " ");
            String numberPart = bookNumber == null ? "" : ("#" + bookNumber + " ");
            return (seriesPart + numberPart + (title == null ? "" : title)).trim();
        }
    }
}
