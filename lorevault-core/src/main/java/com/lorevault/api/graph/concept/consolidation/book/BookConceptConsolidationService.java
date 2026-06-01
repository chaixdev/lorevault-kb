package com.lorevault.api.graph.concept.consolidation.book;

import com.lorevault.api.graph.concept.persistence.BookConcept;
import com.lorevault.api.graph.concept.persistence.ChapterConcept;
import com.lorevault.api.graph.concept.persistence.ChapterConceptGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;
import com.lorevault.api.orchestration.consolidation.PickFirstNonBlank;
import com.lorevault.api.library.book.BookGraphRepository;

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
public class BookConceptConsolidationService {

    private final BookGraphRepository bookGraphRepository;
    private final ChapterConceptGraphRepository chapterConceptRepository;
    private final BookConceptPersistenceService bookConceptPersistenceService;
    private final ConsolidationEngine consolidationEngine;

    public BookConceptConsolidationService(
            BookGraphRepository bookGraphRepository,
            ChapterConceptGraphRepository chapterConceptRepository,
            BookConceptPersistenceService bookConceptPersistenceService,
            ConsolidationEngine consolidationEngine
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.chapterConceptRepository = chapterConceptRepository;
        this.bookConceptPersistenceService = bookConceptPersistenceService;
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
    public BookConceptConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId) {
        if (bookId == null) {
            return new BookConceptConsolidationResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterConcept> chapterConcepts = chapterConceptRepository.findByBookId(bookId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(ChapterConcept::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterConcept::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterConcept::chapterId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ChapterConcept::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        if (chapterConcepts.isEmpty()) {
            bookConceptPersistenceService.replaceBookConcepts(bookId, List.of(), List.of());
            return new BookConceptConsolidationResult(bookId, true, 0, 0, "No chapter concepts found for book");
        }

        return consolidateBook(ctx, bookId, chapterConcepts);
    }

    BookConceptConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId, List<ChapterConcept> chapterConcepts) {
        List<List<ChapterConcept>> clusters = clusterConcepts(chapterConcepts);
        if (clusters.isEmpty()) {
            return new BookConceptConsolidationResult(
                    bookId,
                    false,
                    chapterConcepts.size(),
                    0,
                    "No resolvable chapter concepts found for book"
            );
        }

        List<List<UUID>> chapterConceptIdsByBookConcept = clusters.stream()
                .map(cluster -> cluster.stream().map(ChapterConcept::id).toList())
                .toList();

        List<BookConcept> bookConcepts = clusters.stream()
                .filter(cluster -> !cluster.isEmpty())
                .map(cluster -> {
                    ChapterConcept first = cluster.get(0);
                    LinkedHashSet<String> aliases = new LinkedHashSet<>();
                    String conceptType = null;
                    String description = null;
                    String certainty = null;
                    String evidence = null;
                    for (ChapterConcept col : cluster) {
                        if (col.aliases() != null) {
                            col.aliases().stream()
                                    .filter(a -> a != null && !a.isBlank())
                                    .forEach(aliases::add);
                        }
                        conceptType = PickFirstNonBlank.pick(conceptType, col.conceptType());
                        description = PickFirstNonBlank.pick(description, col.description());
                        certainty = PickFirstNonBlank.pick(certainty, col.certainty());
                        evidence = PickFirstNonBlank.pick(evidence, col.evidence());
                    }
                    return new BookConcept(
                            UUID.randomUUID(),
                            bookId,
                            ctx.stageId(),
                            first.displayName(),
                            first.normalizedName(),
                            List.copyOf(aliases),
                            conceptType,
                            description,
                            certainty,
                            evidence,
                            cluster.size(),
                            first.id(),
                            first.chapterId(),
                            null,
                            null
                    );
                })
                .toList();

        bookConceptPersistenceService.replaceBookConcepts(
                bookId,
                bookConcepts,
                chapterConceptIdsByBookConcept
        );

        return new BookConceptConsolidationResult(
                bookId,
                true,
                chapterConcepts.size(),
                Math.toIntExact(bookConceptPersistenceService.countByBookId(bookId)),
                "Resolved book-level concepts"
        );
    }

    private boolean isResolvable(ChapterConcept chapterConcept) {
        return !NameKeys.from(chapterConcept.normalizedName(), chapterConcept.aliases()).isEmpty();
    }

    private List<List<ChapterConcept>> clusterConcepts(List<ChapterConcept> chapterConcepts) {
        return consolidationEngine.cluster(chapterConcepts, col -> NameKeys.from(col.normalizedName(), col.aliases()));
    }
}
