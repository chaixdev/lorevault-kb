package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.content.association.BookCollective;
import com.lorevault.api.content.association.ChapterCollective;
import com.lorevault.api.content.association.ChapterCollectiveGraphRepository;
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
                .filter(this::isResolvable)
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
        List<CollectiveCluster> clusters = clusterCollectives(chapterCollectives);
        if (clusters.isEmpty()) {
            return new BookCollectiveResolutionResult(
                    bookId,
                    false,
                    chapterCollectives.size(),
                    0,
                    "No resolvable chapter collectives found for book"
            );
        }

        List<BookCollective> bookCollectives = clusters.stream()
                .map(cluster -> new BookCollective(
                        UUID.randomUUID(),
                        bookId,
                        cluster.displayName(),
                        cluster.normalizedName(),
                        List.copyOf(cluster.aliases()),
                        cluster.collectiveType(),
                        cluster.certainty(),
                        cluster.evidence(),
                        cluster.chapterCollectiveIds().size(),
                        cluster.representativeChapterCollectiveId(),
                        cluster.firstSeenChapterId(),
                        null,
                        null
                ))
                .toList();

        List<List<UUID>> chapterCollectiveIdsByBookCollective = clusters.stream()
                .map(CollectiveCluster::chapterCollectiveIds)
                .toList();
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

    private boolean isResolvable(ChapterCollective chapterCollective) {
        return chapterCollective.normalizedName() != null && !chapterCollective.normalizedName().isBlank();
    }

    private List<CollectiveCluster> clusterCollectives(List<ChapterCollective> chapterCollectives) {
        List<CollectiveCluster> clusters = new ArrayList<>();
        for (ChapterCollective chapterCollective : chapterCollectives) {
            int clusterIndex = findClusterByNormalizedName(clusters, chapterCollective.normalizedName());
            if (clusterIndex < 0) {
                clusters.add(CollectiveCluster.from(chapterCollective));
                continue;
            }
            clusters.set(clusterIndex, clusters.get(clusterIndex).add(chapterCollective));
        }
        return clusters;
    }

    private int findClusterByNormalizedName(List<CollectiveCluster> clusters, String normalizedName) {
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i).normalizedName().equals(normalizedName)) {
                return i;
            }
        }
        return -1;
    }

    private static String pickFirstNonBlank(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return null;
    }

    private record CollectiveCluster(
            String displayName,
            String normalizedName,
            LinkedHashSet<String> aliases,
            String collectiveType,
            String certainty,
            String evidence,
            List<UUID> chapterCollectiveIds,
            UUID representativeChapterCollectiveId,
            UUID firstSeenChapterId
    ) {
        static CollectiveCluster from(ChapterCollective chapterCollective) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (chapterCollective.aliases() != null) {
                aliases.addAll(chapterCollective.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            return new CollectiveCluster(
                    chapterCollective.displayName(),
                    chapterCollective.normalizedName(),
                    aliases,
                    chapterCollective.collectiveType(),
                    chapterCollective.certainty(),
                    chapterCollective.evidence(),
                    new ArrayList<>(List.of(chapterCollective.id())),
                    chapterCollective.id(),
                    chapterCollective.chapterId()
            );
        }

        CollectiveCluster add(ChapterCollective chapterCollective) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            if (chapterCollective.aliases() != null) {
                nextAliases.addAll(chapterCollective.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            List<UUID> nextIds = new ArrayList<>(chapterCollectiveIds);
            nextIds.add(chapterCollective.id());
            return new CollectiveCluster(
                    displayName,
                    normalizedName,
                    nextAliases,
                    pickFirstNonBlank(collectiveType, chapterCollective.collectiveType()),
                    pickFirstNonBlank(certainty, chapterCollective.certainty()),
                    pickFirstNonBlank(evidence, chapterCollective.evidence()),
                    nextIds,
                    representativeChapterCollectiveId,
                    firstSeenChapterId
            );
        }
    }
}
