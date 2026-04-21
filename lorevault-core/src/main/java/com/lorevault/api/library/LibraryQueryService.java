package com.lorevault.api.library;

import com.lorevault.api.content.Book;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Series;
import com.lorevault.api.content.Universe;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.SeriesGraphRepository;
import com.lorevault.api.content.UniverseGraphRepository;
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

    private final UniverseGraphRepository universeRepo;
    private final SeriesGraphRepository seriesRepo;
    private final BookGraphRepository bookRepo;
    private final ChapterGraphRepository chapterGraphRepository;

    public List<UniverseSummary> listUniverses() {
        List<UniverseSummary> universes = universeRepo.findAll().stream()
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
        List<SeriesSummary> series = seriesRepo.findByUniverseId(universeId).stream()
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
        List<BookSummary> books = bookRepo.findByUniverseId(universeId).stream()
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
        List<BookSummary> books = bookRepo.findBySeriesId(seriesId).stream()
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

    public record BookSummary(
            UUID id,
            String title,
            Integer bookNumber,
            UUID seriesId,
            String seriesName,
            UUID universeId,
            String universeName
    ) {
        public static BookSummary from(Book book) {
            return new BookSummary(
                    book.getId(),
                    book.getTitle(),
                    book.getBookNumber(),
                    book.getSeriesId(),
                    book.getSeries(),
                    book.getUniverseId(),
                    book.getUniverse()
            );
        }
        
        public String displayLabel() {
            if (seriesName != null && !seriesName.isBlank() && bookNumber != null) {
                return seriesName + " #" + bookNumber + " " + title;
            }
            if (bookNumber != null) {
                return "#" + bookNumber + " " + title;
            }
            return title;
        }
    }
    
    public record ChapterSummary(
            UUID id,
            Integer chapterNumber,
            String title,
            Integer sceneCount
    ) {}

    public List<ChapterSummary> listChaptersForBook(UUID bookId) {
        log.info("[QUERY] Fetching chapters for bookId={}", bookId);
        List<Chapter> chapters = chapterGraphRepository.findByBookId(bookId);
        log.info("[QUERY] Repository returned {} chapters for bookId={}", chapters.size(), bookId);
        
        List<ChapterSummary> summaries = chapters.stream()
                .map(ch -> {
                    UUID nodeBookId = ch.getBook() != null ? ch.getBook().getId() : null;
                    int sceneCount = ch.getScenes() != null ? ch.getScenes().size() : 0;
                    log.info("[QUERY] Chapter: id={}, number={}, title={}, bookId={}, scenes={}, sceneCount={}", 
                            ch.getId(), ch.getChapterNumber(), ch.getChapterTitle(), nodeBookId, 
                            ch.getScenes(), sceneCount);
                    return new ChapterSummary(
                            ch.getId(),
                            ch.getChapterNumber(),
                            ch.getChapterTitle(),
                            sceneCount
                    );
                })
                .toList();
                
        return summaries;
    }
}
