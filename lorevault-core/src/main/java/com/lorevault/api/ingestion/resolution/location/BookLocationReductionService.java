package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.content.association.BookLocation;
import com.lorevault.api.content.association.BookLocationGraphRepository;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.content.association.ChapterLocation;
import com.lorevault.api.content.association.ChapterLocationGraphRepository;
import com.lorevault.api.ingestion.resolution.consolidation.ConsolidationEngine;
import com.lorevault.api.ingestion.resolution.consolidation.NameKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.neo4j.driver.exceptions.TransientException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookLocationReductionService {

    private final BookLocationGraphRepository bookLocationRepository;
    private final BookGraphRepository bookGraphRepository;
    private final ChapterLocationGraphRepository chapterLocationRepository;
    private final BookLocationPersistenceService bookLocationPersistenceService;

    public BookLocationReductionService(
            BookLocationGraphRepository bookLocationRepository,
            BookGraphRepository bookGraphRepository,
            ChapterLocationGraphRepository chapterLocationRepository,
            BookLocationPersistenceService bookLocationPersistenceService
    ) {
        this.bookLocationRepository = bookLocationRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.chapterLocationRepository = chapterLocationRepository;
        this.bookLocationPersistenceService = bookLocationPersistenceService;
    }

    @Transactional(readOnly = true)
    public boolean bookExists(UUID bookId) {
        return bookId != null && bookGraphRepository.findById(bookId).isPresent();
    }

    @Retryable(
            retryFor = {TransientDataAccessException.class, TransientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2_000)
    )
    public BookLocationResolutionResult resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookLocationResolutionResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterLocation> chapterLocations = chapterLocationRepository.findByBookId(bookId).stream()
                .filter(cl -> !NameKeys.from(cl.normalizedName(), cl.aliases()).isEmpty())
                .sorted(Comparator
                        .comparing(ChapterLocation::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterLocation::displayName, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (chapterLocations.isEmpty()) {
            bookLocationPersistenceService.replaceBookLocations(bookId, List.of(), List.of());
            return new BookLocationResolutionResult(bookId, true, 0, 0, "No chapter locations found for book");
        }
        return resolveBook(bookId, chapterLocations);
    }

    BookLocationResolutionResult resolveBook(UUID bookId, List<ChapterLocation> chapterLocations) {

        List<List<ChapterLocation>> clusters = ConsolidationEngine.cluster(
                chapterLocations,
                cl -> NameKeys.from(cl.normalizedName(), cl.aliases())
        );
        if (clusters.isEmpty()) {
            return new BookLocationResolutionResult(bookId, false, chapterLocations.size(), 0, "No resolvable chapter locations found for book");
        }

        List<BookLocation> bookLocations = new ArrayList<>();
        List<List<UUID>> chapterLocationIdsByBookLocation = new ArrayList<>();

        for (List<ChapterLocation> cluster : clusters) {
            ChapterLocation representative = cluster.get(0);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            List<UUID> chapterLocationIds = new ArrayList<>();
            for (ChapterLocation cl : cluster) {
                chapterLocationIds.add(cl.id());
                if (cl.aliases() != null) {
                    cl.aliases().stream()
                            .filter(a -> a != null && !a.isBlank())
                            .forEach(aliases::add);
                }
            }
            chapterLocationIdsByBookLocation.add(chapterLocationIds);

            bookLocations.add(new BookLocation(
                    UUID.randomUUID(),
                    bookId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    cluster.size(),
                    representative.id(),
                    representative.chapterId(),
                    null,
                    null
            ));
        }

        bookLocationPersistenceService.replaceBookLocations(bookId, bookLocations, chapterLocationIdsByBookLocation);

        return new BookLocationResolutionResult(
                bookId,
                true,
                chapterLocations.size(),
                Math.toIntExact(bookLocationPersistenceService.countByBookId(bookId)),
                "Resolved book-level locations"
        );
    }
}
