package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.content.association.BookObject;
import com.lorevault.api.content.association.ChapterObject;
import com.lorevault.api.content.association.ChapterObjectGraphRepository;
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
        List<List<ChapterObject>> clusters = ConsolidationEngine.cluster(
                chapterObjects,
                co -> NameKeys.from(co.normalizedName(), co.aliases())
        );

        if (clusters.isEmpty()) {
            return new BookObjectResolutionResult(bookId, false, chapterObjects.size(), 0, "No resolvable chapter objects found for book");
        }

        List<BookObject> bookObjects = new ArrayList<>();
        List<List<UUID>> chapterObjectIdsByBookObject = new ArrayList<>();

        for (List<ChapterObject> cluster : clusters) {
            ChapterObject representative = cluster.get(0);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            List<UUID> chapterObjectIds = new ArrayList<>();
            String type = null;
            String material = null;
            String purpose = null;
            String description = null;
            UUID firstSeenChapterId = null;
            for (ChapterObject co : cluster) {
                chapterObjectIds.add(co.id());
                if (co.aliases() != null) {
                    co.aliases().stream()
                            .filter(a -> a != null && !a.isBlank())
                            .forEach(aliases::add);
                }
                type = PickFirstNonBlank.pick(type, co.type());
                material = PickFirstNonBlank.pick(material, co.material());
                purpose = PickFirstNonBlank.pick(purpose, co.purpose());
                description = PickFirstNonBlank.pick(description, co.description());
                if (firstSeenChapterId == null) {
                    firstSeenChapterId = co.chapterId();
                }
            }
            chapterObjectIdsByBookObject.add(chapterObjectIds);
            bookObjects.add(new BookObject(
                    UUID.randomUUID(),
                    bookId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    type,
                    material,
                    purpose,
                    description,
                    cluster.size(),
                    representative.id(),
                    firstSeenChapterId,
                    null,
                    null
            ));
        }

        bookObjectPersistenceService.replaceBookObjects(bookId, bookObjects, chapterObjectIdsByBookObject);

        return new BookObjectResolutionResult(
                bookId,
                true,
                chapterObjects.size(),
                Math.toIntExact(bookObjectPersistenceService.countByBookId(bookId)),
                "Resolved book-level objects"
        );
    }
}
