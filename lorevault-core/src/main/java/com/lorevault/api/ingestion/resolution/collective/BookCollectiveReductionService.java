package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.content.association.BookCollective;
import com.lorevault.api.content.association.ChapterCollective;
import com.lorevault.api.content.association.ChapterCollectiveGraphRepository;
import com.lorevault.api.ingestion.resolution.consolidation.ConsolidationEngine;
import com.lorevault.api.ingestion.resolution.consolidation.NameKeys;
import com.lorevault.api.ingestion.resolution.consolidation.PickFirstNonBlank;
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
public class BookCollectiveReductionService {

    private final BookGraphRepository bookGraphRepository;
    private final ChapterCollectiveGraphRepository chapterCollectiveRepository;
    private final BookCollectivePersistenceService bookCollectivePersistenceService;

    public BookCollectiveReductionService(
            BookGraphRepository bookGraphRepository,
            ChapterCollectiveGraphRepository chapterCollectiveRepository,
            BookCollectivePersistenceService bookCollectivePersistenceService
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.chapterCollectiveRepository = chapterCollectiveRepository;
        this.bookCollectivePersistenceService = bookCollectivePersistenceService;
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
    public BookCollectiveResolutionResult resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookCollectiveResolutionResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterCollective> chapterCollectives = chapterCollectiveRepository.findByBookId(bookId).stream()
                .sorted(Comparator
                        .comparing(ChapterCollective::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterCollective::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterCollective::chapterId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ChapterCollective::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        if (chapterCollectives.isEmpty()) {
            bookCollectivePersistenceService.replaceBookCollectives(bookId, List.of(), List.of());
            return new BookCollectiveResolutionResult(bookId, true, 0, 0, "No chapter collectives found for book");
        }

        return resolveBook(bookId, chapterCollectives);
    }

    BookCollectiveResolutionResult resolveBook(UUID bookId, List<ChapterCollective> chapterCollectives) {
        List<List<ChapterCollective>> clusters = ConsolidationEngine.cluster(
                chapterCollectives,
                cc -> NameKeys.from(cc.normalizedName(), cc.aliases())
        );
        if (clusters.isEmpty()) {
            return new BookCollectiveResolutionResult(
                    bookId,
                    false,
                    chapterCollectives.size(),
                    0,
                    "No resolvable chapter collectives found for book"
            );
        }

        List<List<UUID>> chapterCollectiveIdsByBookCollective = new ArrayList<>();
        List<BookCollective> bookCollectives = new ArrayList<>();
        for (List<ChapterCollective> cluster : clusters) {
            ChapterCollective representative = cluster.get(0);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            String collectiveType = null;
            String certainty = null;
            String evidence = null;
            List<UUID> chapterCollectiveIds = new ArrayList<>();
            for (ChapterCollective cc : cluster) {
                if (cc.aliases() != null) {
                    for (String alias : cc.aliases()) {
                        if (alias != null && !alias.isBlank()) {
                            aliases.add(alias);
                        }
                    }
                }
                collectiveType = PickFirstNonBlank.pick(collectiveType, cc.collectiveType());
                certainty = PickFirstNonBlank.pick(certainty, cc.certainty());
                evidence = PickFirstNonBlank.pick(evidence, cc.evidence());
                chapterCollectiveIds.add(cc.id());
            }
            chapterCollectiveIdsByBookCollective.add(chapterCollectiveIds);

            bookCollectives.add(new BookCollective(
                    UUID.randomUUID(),
                    bookId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    collectiveType,
                    certainty,
                    evidence,
                    cluster.size(),
                    representative.id(),
                    representative.chapterId(),
                    null,
                    null
            ));
        }

        bookCollectivePersistenceService.replaceBookCollectives(
                bookId,
                bookCollectives,
                chapterCollectiveIdsByBookCollective
        );

        return new BookCollectiveResolutionResult(
                bookId,
                true,
                chapterCollectives.size(),
                Math.toIntExact(bookCollectivePersistenceService.countByBookId(bookId)),
                "Resolved book-level collectives"
        );
    }
}
