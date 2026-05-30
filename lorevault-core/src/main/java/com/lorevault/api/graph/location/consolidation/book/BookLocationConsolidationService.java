package com.lorevault.api.graph.location.consolidation.book;

import com.lorevault.api.graph.location.persistence.BookLocation;
import com.lorevault.api.graph.location.persistence.BookLocationGraphRepository;
import com.lorevault.api.graph.location.persistence.ChapterLocation;
import com.lorevault.api.graph.location.persistence.ChapterLocationGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;
import com.lorevault.api.library.book.BookGraphRepository;

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
public class BookLocationConsolidationService {

    private final BookLocationGraphRepository bookLocationRepository;
    private final BookGraphRepository bookGraphRepository;
    private final ChapterLocationGraphRepository chapterLocationRepository;
    private final BookLocationPersistenceService bookLocationPersistenceService;
    private final ConsolidationEngine consolidationEngine;

    public BookLocationConsolidationService(
            BookLocationGraphRepository bookLocationRepository,
            BookGraphRepository bookGraphRepository,
            ChapterLocationGraphRepository chapterLocationRepository,
            BookLocationPersistenceService bookLocationPersistenceService,
            ConsolidationEngine consolidationEngine
    ) {
        this.bookLocationRepository = bookLocationRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.chapterLocationRepository = chapterLocationRepository;
        this.bookLocationPersistenceService = bookLocationPersistenceService;
        this.consolidationEngine = consolidationEngine;
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
    public BookLocationConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId) {
        if (bookId == null) {
            return new BookLocationConsolidationResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterLocation> chapterLocations = chapterLocationRepository.findByBookId(bookId).stream()
                .filter(loc -> !NameKeys.from(loc.normalizedName(), loc.aliases()).isEmpty())
                .sorted(Comparator
                        .comparing(ChapterLocation::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterLocation::displayName, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (chapterLocations.isEmpty()) {
            bookLocationPersistenceService.replaceBookLocations(bookId, List.of(), List.of());
            return new BookLocationConsolidationResult(bookId, true, 0, 0, "No chapter locations found for book");
        }
        return consolidateBook(ctx, bookId, chapterLocations);
    }

    BookLocationConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId, List<ChapterLocation> chapterLocations) {

        List<List<ChapterLocation>> clusters = consolidationEngine.cluster(chapterLocations,
                loc -> NameKeys.from(loc.normalizedName(), loc.aliases()));

        if (clusters.isEmpty()) {
            return new BookLocationConsolidationResult(bookId, false, chapterLocations.size(), 0,
                    "No resolvable chapter locations found for book");
        }

        List<BookLocation> bookLocations = new ArrayList<>();
        List<List<UUID>> chapterLocationIdsByBookLocation = new ArrayList<>();
        for (List<ChapterLocation> cluster : clusters) {
            ChapterLocation first = cluster.get(0);
            LinkedHashSet<String> mergedAliases = new LinkedHashSet<>();
            List<UUID> chapterLocIds = new ArrayList<>();
            for (ChapterLocation loc : cluster) {
                if (loc.aliases() != null) {
                    for (String alias : loc.aliases()) {
                        if (alias != null && !alias.isBlank()) {
                            mergedAliases.add(alias);
                        }
                    }
                }
                chapterLocIds.add(loc.id());
            }
            bookLocations.add(new BookLocation(
                    UUID.randomUUID(),
                    bookId,
                    ctx.stageId(),
                    first.displayName(),
                    first.normalizedName(),
                    List.copyOf(mergedAliases),
                    chapterLocIds.size(),
                    first.id(),
                    first.chapterId(),
                    null,
                    null
            ));
            chapterLocationIdsByBookLocation.add(chapterLocIds);
        }

        bookLocationPersistenceService.replaceBookLocations(bookId, bookLocations, chapterLocationIdsByBookLocation);

        return new BookLocationConsolidationResult(
                bookId,
                true,
                chapterLocations.size(),
                Math.toIntExact(bookLocationPersistenceService.countByBookId(bookId)),
                "Resolved book-level locations"
        );
    }
}
