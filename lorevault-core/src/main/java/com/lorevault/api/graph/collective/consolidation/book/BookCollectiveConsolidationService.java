package com.lorevault.api.graph.collective.consolidation.book;

import com.lorevault.api.graph.collective.persistence.BookCollective;
import com.lorevault.api.graph.collective.persistence.ChapterCollective;
import com.lorevault.api.graph.collective.persistence.ChapterCollectiveGraphRepository;
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
public class BookCollectiveConsolidationService {

    private final BookGraphRepository bookGraphRepository;
    private final ChapterCollectiveGraphRepository chapterCollectiveRepository;
    private final BookCollectivePersistenceService bookCollectivePersistenceService;
    private final ConsolidationEngine consolidationEngine;

    public BookCollectiveConsolidationService(
            BookGraphRepository bookGraphRepository,
            ChapterCollectiveGraphRepository chapterCollectiveRepository,
            BookCollectivePersistenceService bookCollectivePersistenceService,
            ConsolidationEngine consolidationEngine
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.chapterCollectiveRepository = chapterCollectiveRepository;
        this.bookCollectivePersistenceService = bookCollectivePersistenceService;
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
    public BookCollectiveConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId) {
        if (bookId == null) {
            return new BookCollectiveConsolidationResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterCollective> chapterCollectives = chapterCollectiveRepository.findByBookId(bookId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(ChapterCollective::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterCollective::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterCollective::chapterId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ChapterCollective::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        if (chapterCollectives.isEmpty()) {
            bookCollectivePersistenceService.replaceBookCollectives(bookId, List.of(), List.of());
            return new BookCollectiveConsolidationResult(bookId, true, 0, 0, "No chapter collectives found for book");
        }

        return consolidateBook(ctx, bookId, chapterCollectives);
    }

    BookCollectiveConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId, List<ChapterCollective> chapterCollectives) {
        List<List<ChapterCollective>> clusters = clusterCollectives(chapterCollectives);
        if (clusters.isEmpty()) {
            return new BookCollectiveConsolidationResult(
                    bookId,
                    false,
                    chapterCollectives.size(),
                    0,
                    "No resolvable chapter collectives found for book"
            );
        }

        List<List<UUID>> chapterCollectiveIdsByBookCollective = clusters.stream()
                .map(cluster -> cluster.stream().map(ChapterCollective::id).toList())
                .toList();

        List<BookCollective> bookCollectives = clusters.stream()
                .map(cluster -> {
                    ChapterCollective first = cluster.get(0);
                    LinkedHashSet<String> aliases = new LinkedHashSet<>();
                    String collectiveType = null;
                    String certainty = null;
                    String evidence = null;
                    for (ChapterCollective col : cluster) {
                        if (col.aliases() != null) {
                            col.aliases().stream()
                                    .filter(a -> a != null && !a.isBlank())
                                    .forEach(aliases::add);
                        }
                        collectiveType = PickFirstNonBlank.pick(collectiveType, col.collectiveType());
                        certainty = PickFirstNonBlank.pick(certainty, col.certainty());
                        evidence = PickFirstNonBlank.pick(evidence, col.evidence());
                    }
                    return new BookCollective(
                            UUID.randomUUID(),
                            bookId,
                            ctx.stageId(),
                            first.displayName(),
                            first.normalizedName(),
                            List.copyOf(aliases),
                            collectiveType,
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

        bookCollectivePersistenceService.replaceBookCollectives(
                bookId,
                bookCollectives,
                chapterCollectiveIdsByBookCollective
        );

        return new BookCollectiveConsolidationResult(
                bookId,
                true,
                chapterCollectives.size(),
                Math.toIntExact(bookCollectivePersistenceService.countByBookId(bookId)),
                "Resolved book-level collectives"
        );
    }

    private boolean isResolvable(ChapterCollective chapterCollective) {
        return !NameKeys.from(chapterCollective.normalizedName(), chapterCollective.aliases()).isEmpty();
    }

    private List<List<ChapterCollective>> clusterCollectives(List<ChapterCollective> chapterCollectives) {
        return consolidationEngine.cluster(chapterCollectives, col -> NameKeys.from(col.normalizedName(), col.aliases()));
    }
}
