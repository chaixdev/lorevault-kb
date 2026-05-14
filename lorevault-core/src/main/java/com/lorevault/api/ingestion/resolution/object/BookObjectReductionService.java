package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.content.association.BookObject;
import com.lorevault.api.content.association.ChapterObject;
import com.lorevault.api.content.association.ChapterObjectGraphRepository;
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
public class BookObjectReductionService {

    private final BookGraphRepository bookGraphRepository;
    private final ChapterObjectGraphRepository chapterObjectRepository;
    private final BookObjectPersistenceService bookObjectPersistenceService;

    public BookObjectReductionService(
            BookGraphRepository bookGraphRepository,
            ChapterObjectGraphRepository chapterObjectRepository,
            BookObjectPersistenceService bookObjectPersistenceService
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.chapterObjectRepository = chapterObjectRepository;
        this.bookObjectPersistenceService = bookObjectPersistenceService;
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
    public BookObjectResolutionResult resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookObjectResolutionResult(null, false, 0, 0, "Book ID is required");
        }

        List<ChapterObject> chapterObjects = chapterObjectRepository.findByBookId(bookId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(ChapterObject::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterObject::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterObject::chapterId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ChapterObject::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        if (chapterObjects.isEmpty()) {
            bookObjectPersistenceService.replaceBookObjects(bookId, List.of(), List.of());
            return new BookObjectResolutionResult(bookId, true, 0, 0, "No chapter objects found for book");
        }

        return resolveBook(bookId, chapterObjects);
    }

    BookObjectResolutionResult resolveBook(UUID bookId, List<ChapterObject> chapterObjects) {
        List<ObjectCluster> clusters = clusterObjects(chapterObjects);
        if (clusters.isEmpty()) {
            return new BookObjectResolutionResult(bookId, false, chapterObjects.size(), 0, "No resolvable chapter objects found for book");
        }

        List<BookObject> bookObjects = clusters.stream()
                .map(cluster -> new BookObject(
                        UUID.randomUUID(),
                        bookId,
                        cluster.displayName(),
                        cluster.normalizedName(),
                        List.copyOf(cluster.aliases()),
                        cluster.type(),
                        cluster.material(),
                        cluster.purpose(),
                        cluster.description(),
                        cluster.chapterObjectIds().size(),
                        cluster.representativeChapterObjectId(),
                        cluster.firstSeenChapterId(),
                        null,
                        null
                ))
                .toList();

        List<List<UUID>> chapterObjectIdsByBookObject = clusters.stream()
                .map(ObjectCluster::chapterObjectIds)
                .toList();
        bookObjectPersistenceService.replaceBookObjects(bookId, bookObjects, chapterObjectIdsByBookObject);

        return new BookObjectResolutionResult(
                bookId,
                true,
                chapterObjects.size(),
                Math.toIntExact(bookObjectPersistenceService.countByBookId(bookId)),
                "Resolved book-level objects"
        );
    }

    private boolean isResolvable(ChapterObject chapterObject) {
        return chapterObject.normalizedName() != null && !chapterObject.normalizedName().isBlank();
    }

    private List<ObjectCluster> clusterObjects(List<ChapterObject> chapterObjects) {
        List<ObjectCluster> clusters = new ArrayList<>();
        for (ChapterObject chapterObject : chapterObjects) {
            int clusterIndex = findClusterByNormalizedName(clusters, chapterObject.normalizedName());
            if (clusterIndex < 0) {
                clusters.add(ObjectCluster.from(chapterObject));
                continue;
            }
            clusters.set(clusterIndex, clusters.get(clusterIndex).add(chapterObject));
        }
        return clusters;
    }

    private int findClusterByNormalizedName(List<ObjectCluster> clusters, String normalizedName) {
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

    private record ObjectCluster(
            String displayName,
            String normalizedName,
            LinkedHashSet<String> aliases,
            String type,
            String material,
            String purpose,
            String description,
            List<UUID> chapterObjectIds,
            UUID representativeChapterObjectId,
            UUID firstSeenChapterId
    ) {
        static ObjectCluster from(ChapterObject chapterObject) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (chapterObject.aliases() != null) {
                aliases.addAll(chapterObject.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            return new ObjectCluster(
                    chapterObject.displayName(),
                    chapterObject.normalizedName(),
                    aliases,
                    chapterObject.type(),
                    chapterObject.material(),
                    chapterObject.purpose(),
                    chapterObject.description(),
                    new ArrayList<>(List.of(chapterObject.id())),
                    chapterObject.id(),
                    chapterObject.chapterId()
            );
        }

        ObjectCluster add(ChapterObject chapterObject) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            if (chapterObject.aliases() != null) {
                nextAliases.addAll(chapterObject.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            List<UUID> nextIds = new ArrayList<>(chapterObjectIds);
            nextIds.add(chapterObject.id());
            return new ObjectCluster(
                    displayName,
                    normalizedName,
                    nextAliases,
                    pickFirstNonBlank(type, chapterObject.type()),
                    pickFirstNonBlank(material, chapterObject.material()),
                    pickFirstNonBlank(purpose, chapterObject.purpose()),
                    pickFirstNonBlank(description, chapterObject.description()),
                    nextIds,
                    representativeChapterObjectId,
                    firstSeenChapterId
            );
        }
    }
}
