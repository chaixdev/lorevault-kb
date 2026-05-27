package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.content.association.BookIndividual;
import com.lorevault.api.content.association.ChapterIndividual;
import com.lorevault.api.content.association.ChapterIndividualGraphRepository;
import com.lorevault.api.ingestion.resolution.consolidation.ConsolidationEngine;
import com.lorevault.api.ingestion.resolution.consolidation.NameKeys;
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
public class BookIndividualReductionService {

    private final BookGraphRepository bookGraphRepository;
    private final ChapterIndividualGraphRepository chapterIndividualRepository;
    private final BookIndividualPersistenceService bookIndividualPersistenceService;

    public BookIndividualReductionService(
            BookGraphRepository bookGraphRepository,
            ChapterIndividualGraphRepository chapterIndividualRepository,
            BookIndividualPersistenceService bookIndividualPersistenceService
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.chapterIndividualRepository = chapterIndividualRepository;
        this.bookIndividualPersistenceService = bookIndividualPersistenceService;
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
    public BookIndividualResolutionResult resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookIndividualResolutionResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterIndividual> chapterIndividuals = chapterIndividualRepository.findByBookId(bookId).stream()
                .sorted(Comparator
                        .comparing(ChapterIndividual::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterIndividual::displayName, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (chapterIndividuals.isEmpty()) {
            bookIndividualPersistenceService.replaceBookIndividuals(bookId, List.of(), List.of());
            return new BookIndividualResolutionResult(bookId, true, 0, 0, "No chapter individuals found for book");
        }

        return resolveBook(bookId, chapterIndividuals);
    }

    BookIndividualResolutionResult resolveBook(UUID bookId, List<ChapterIndividual> chapterIndividuals) {

        List<List<ChapterIndividual>> clusters =
                ConsolidationEngine.cluster(chapterIndividuals, ci -> NameKeys.from(ci.normalizedName(), ci.aliases()));

        if (clusters.isEmpty()) {
            return new BookIndividualResolutionResult(bookId, false, chapterIndividuals.size(), 0, "No resolvable chapter individuals found for book");
        }

        List<BookIndividual> bookIndividuals = new ArrayList<>();
        List<List<UUID>> chapterIndividualIdsByBookIndividual = new ArrayList<>();

        for (List<ChapterIndividual> cluster : clusters) {
            ChapterIndividual representative = cluster.get(0);
            List<UUID> chapterIndividualIds = cluster.stream().map(ChapterIndividual::id).toList();
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            for (ChapterIndividual ci : cluster) {
                if (ci.aliases() != null) {
                    aliases.addAll(ci.aliases().stream().filter(a -> a != null && !a.isBlank()).toList());
                }
            }
            bookIndividuals.add(new BookIndividual(
                    UUID.randomUUID(),
                    bookId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    chapterIndividualIds.size(),
                    representative.id(),
                    representative.chapterId(),
                    null,
                    null
            ));
            chapterIndividualIdsByBookIndividual.add(chapterIndividualIds);
        }

        bookIndividualPersistenceService.replaceBookIndividuals(bookId, bookIndividuals, chapterIndividualIdsByBookIndividual);

        return new BookIndividualResolutionResult(
                bookId,
                true,
                chapterIndividuals.size(),
                Math.toIntExact(bookIndividualPersistenceService.countByBookId(bookId)),
                "Resolved book-level individuals"
        );
    }
}
