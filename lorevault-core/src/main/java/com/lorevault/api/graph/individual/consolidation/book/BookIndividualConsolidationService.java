package com.lorevault.api.graph.individual.consolidation.book;

import com.lorevault.api.graph.individual.persistence.BookIndividual;
import com.lorevault.api.graph.individual.persistence.BookIndividualGraphRepository;
import com.lorevault.api.graph.individual.persistence.ChapterIndividual;
import com.lorevault.api.graph.individual.persistence.ChapterIndividualGraphRepository;
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
public class BookIndividualConsolidationService {

    private final BookIndividualGraphRepository bookIndividualRepository;
    private final BookGraphRepository bookGraphRepository;
    private final ChapterIndividualGraphRepository chapterIndividualRepository;
    private final BookIndividualPersistenceService bookIndividualPersistenceService;
    private final ConsolidationEngine consolidationEngine;

    public BookIndividualConsolidationService(
            BookIndividualGraphRepository bookIndividualRepository,
            BookGraphRepository bookGraphRepository,
            ChapterIndividualGraphRepository chapterIndividualRepository,
            BookIndividualPersistenceService bookIndividualPersistenceService,
            ConsolidationEngine consolidationEngine
    ) {
        this.bookIndividualRepository = bookIndividualRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.chapterIndividualRepository = chapterIndividualRepository;
        this.bookIndividualPersistenceService = bookIndividualPersistenceService;
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
    public BookIndividualConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId) {
        if (bookId == null) {
            return new BookIndividualConsolidationResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterIndividual> chapterIndividuals = chapterIndividualRepository.findByBookId(bookId).stream()
                .filter(ci -> !NameKeys.from(ci.normalizedName(), ci.aliases()).isEmpty())
                .sorted(Comparator
                        .comparing(ChapterIndividual::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterIndividual::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterIndividual::chapterId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChapterIndividual::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (chapterIndividuals.isEmpty()) {
            bookIndividualPersistenceService.replaceBookIndividuals(bookId, List.of(), List.of());
            return new BookIndividualConsolidationResult(bookId, true, 0, 0, "No chapter individuals found for book");
        }
        return consolidateBook(ctx, bookId, chapterIndividuals);
    }

    BookIndividualConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId, List<ChapterIndividual> chapterIndividuals) {

        List<List<ChapterIndividual>> clusters = consolidationEngine.cluster(chapterIndividuals,
                ci -> NameKeys.from(ci.normalizedName(), ci.aliases()));

        if (clusters.isEmpty()) {
            return new BookIndividualConsolidationResult(bookId, false, chapterIndividuals.size(), 0,
                    "No resolvable chapter individuals found for book");
        }

        List<BookIndividual> bookIndividuals = new ArrayList<>();
        List<List<UUID>> chapterIndividualIdsByBookIndividual = new ArrayList<>();
        for (List<ChapterIndividual> cluster : clusters) {
            ChapterIndividual first = cluster.get(0);
            LinkedHashSet<String> mergedAliases = new LinkedHashSet<>();
            List<UUID> ciIds = new ArrayList<>();
            for (ChapterIndividual ci : cluster) {
                if (ci.aliases() != null) {
                    for (String alias : ci.aliases()) {
                        if (alias != null && !alias.isBlank()) {
                            mergedAliases.add(alias);
                        }
                    }
                }
                ciIds.add(ci.id());
            }
            bookIndividuals.add(new BookIndividual(
                    UUID.randomUUID(),
                    bookId,
                    ctx.stageId(),
                    first.displayName(),
                    first.normalizedName(),
                    List.copyOf(mergedAliases),
                    ciIds.size(),
                    first.id(),
                    first.chapterId(),
                    null,
                    null
            ));
            chapterIndividualIdsByBookIndividual.add(ciIds);
        }

        bookIndividualPersistenceService.replaceBookIndividuals(bookId, bookIndividuals, chapterIndividualIdsByBookIndividual);

        return new BookIndividualConsolidationResult(
                bookId,
                true,
                chapterIndividuals.size(),
                Math.toIntExact(bookIndividualPersistenceService.countByBookId(bookId)),
                "Resolved book-level individuals"
        );
    }
}
