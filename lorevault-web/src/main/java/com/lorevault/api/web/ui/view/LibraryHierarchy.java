package com.lorevault.api.web.ui.view;

import com.lorevault.api.library.service.LibraryQueryService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hierarchical view model for displaying books grouped by universe and series
 */
public record LibraryHierarchy(List<UniverseGroup> universes) {

    public record UniverseGroup(
            UUID id,
            String name,
            List<SeriesGroup> series,
            List<BookCard> standaloneBooks
    ) {}

    public record SeriesGroup(
            UUID id,
            String name,
            List<BookCard> books
    ) {}

    public record BookCard(
            UUID id,
            String title,
            Integer bookNumber,
            String seriesName,
            String universeName
    ) {
        public String displayTitle() {
            if (bookNumber != null) {
                return "#" + bookNumber + " " + title;
            }
            return title;
        }
    }

    public static LibraryHierarchy from(List<LibraryQueryService.BookSummary> allBooks) {
        // Group books by universe
        Map<UUID, List<LibraryQueryService.BookSummary>> byUniverse = allBooks.stream()
                .collect(Collectors.groupingBy(LibraryQueryService.BookSummary::universeId));

        List<UniverseGroup> universes = byUniverse.entrySet().stream()
                .map(entry -> {
                    UUID universeId = entry.getKey();
                    List<LibraryQueryService.BookSummary> booksInUniverse = entry.getValue();
                    String universeName = booksInUniverse.isEmpty() ? "" : booksInUniverse.get(0).universeName();

                    // Separate standalone books from series books
                    Map<Boolean, List<LibraryQueryService.BookSummary>> partitioned = booksInUniverse.stream()
                            .collect(Collectors.partitioningBy(b -> b.seriesId() != null));

                    List<BookCard> standaloneBooks = partitioned.get(false).stream()
                            .map(b -> new BookCard(b.id(), b.title(), null, null, b.universeName()))
                            .toList();

                    // Group series books by series
                    Map<UUID, List<LibraryQueryService.BookSummary>> bySeries = partitioned.get(true).stream()
                            .collect(Collectors.groupingBy(LibraryQueryService.BookSummary::seriesId));

                    List<SeriesGroup> seriesGroups = bySeries.entrySet().stream()
                            .map(seriesEntry -> {
                                List<LibraryQueryService.BookSummary> booksInSeries = seriesEntry.getValue();
                                String seriesName = booksInSeries.isEmpty() ? "" : booksInSeries.get(0).seriesName();
                                List<BookCard> books = booksInSeries.stream()
                                        .map(b -> new BookCard(b.id(), b.title(), b.bookNumber(), b.seriesName(), b.universeName()))
                                        .toList();
                                return new SeriesGroup(seriesEntry.getKey(), seriesName, books);
                            })
                            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                            .toList();

                    return new UniverseGroup(universeId, universeName, seriesGroups, standaloneBooks);
                })
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();

        return new LibraryHierarchy(universes);
    }
}
