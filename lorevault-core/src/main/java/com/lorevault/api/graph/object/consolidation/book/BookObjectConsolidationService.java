package com.lorevault.api.graph.object.consolidation.book;

import com.lorevault.api.graph.object.persistence.BookObject;
import com.lorevault.api.graph.object.persistence.ChapterObject;
import com.lorevault.api.graph.object.persistence.ChapterObjectGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;
import com.lorevault.api.orchestration.consolidation.PickFirstNonBlank;
import com.lorevault.api.library.book.BookGraphRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.neo4j.driver.exceptions.TransientException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookObjectConsolidationService {

    private final BookGraphRepository bookGraphRepository;
    private final ChapterObjectGraphRepository chapterObjectRepository;
    private final BookObjectPersistenceService bookObjectPersistenceService;
    private final ConsolidationEngine consolidationEngine;

    public BookObjectConsolidationService(
            BookGraphRepository bookGraphRepository,
            ChapterObjectGraphRepository chapterObjectRepository,
            BookObjectPersistenceService bookObjectPersistenceService,
            ConsolidationEngine consolidationEngine
    ) {
        this.bookGraphRepository = bookGraphRepository;
        this.chapterObjectRepository = chapterObjectRepository;
        this.bookObjectPersistenceService = bookObjectPersistenceService;
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
    public BookObjectConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId) {
        if (bookId == null) {
            return new BookObjectConsolidationResult(null, false, 0, 0, "Book ID is required");
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
            return new BookObjectConsolidationResult(bookId, true, 0, 0, "No chapter objects found for book");
        }

        return consolidateBook(ctx, bookId, chapterObjects);
    }

    BookObjectConsolidationResult consolidateBook(StageExecutionContext ctx, UUID bookId, List<ChapterObject> chapterObjects) {
        List<List<ChapterObject>> clusters = clusterObjects(chapterObjects);
        if (clusters.isEmpty()) {
            return new BookObjectConsolidationResult(bookId, false, chapterObjects.size(), 0, "No resolvable chapter objects found for book");
        }

        List<BookObject> bookObjects = new ArrayList<>();
        List<List<UUID>> chapterObjectIdsByBookObject = new ArrayList<>();
        for (List<ChapterObject> cluster : clusters) {
            ChapterObject first = cluster.get(0);
            List<String> aliases = cluster.stream()
                    .filter(o -> o.aliases() != null)
                    .flatMap(o -> o.aliases().stream())
                    .filter(a -> a != null && !a.isBlank())
                    .distinct()
                    .toList();
            String type = cluster.stream().map(ChapterObject::type).reduce(null, PickFirstNonBlank::pick);
            String material = cluster.stream().map(ChapterObject::material).reduce(null, PickFirstNonBlank::pick);
            String purpose = cluster.stream().map(ChapterObject::purpose).reduce(null, PickFirstNonBlank::pick);
            String description = cluster.stream().map(ChapterObject::description).reduce(null, PickFirstNonBlank::pick);
            List<UUID> chapterObjectIds = cluster.stream().map(ChapterObject::id).toList();
            bookObjects.add(new BookObject(
                    UUID.randomUUID(),
                    bookId,
                    ctx.stageId(),
                    first.displayName(),
                    first.normalizedName(),
                    aliases,
                    type,
                    material,
                    purpose,
                    description,
                    chapterObjectIds.size(),
                    first.id(),
                    first.chapterId(),
                    null,
                    null
            ));
            chapterObjectIdsByBookObject.add(chapterObjectIds);
        }

        bookObjectPersistenceService.replaceBookObjects(bookId, bookObjects, chapterObjectIdsByBookObject);

        return new BookObjectConsolidationResult(
                bookId,
                true,
                chapterObjects.size(),
                Math.toIntExact(bookObjectPersistenceService.countByBookId(bookId)),
                "Resolved book-level objects"
        );
    }

    private boolean isResolvable(ChapterObject chapterObject) {
        return !NameKeys.from(chapterObject.normalizedName(), chapterObject.aliases()).isEmpty();
    }

    private List<List<ChapterObject>> clusterObjects(List<ChapterObject> chapterObjects) {
        return consolidationEngine.cluster(chapterObjects, obj -> NameKeys.from(obj.normalizedName(), obj.aliases()));
    }
}
