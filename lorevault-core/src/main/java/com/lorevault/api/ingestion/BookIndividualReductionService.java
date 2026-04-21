package com.lorevault.api.ingestion;

import com.lorevault.api.content.BookIndividual;
import com.lorevault.api.content.BookIndividualGraphRepository;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.support.BookIndividualResolutionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookIndividualReductionService {

    private final BookIndividualGraphRepository bookIndividualRepository;
    private final BookGraphRepository bookGraphRepository;
    private final Neo4jClient neo4jClient;
    private final ConcurrentHashMap<UUID, ReentrantLock> bookLocks = new ConcurrentHashMap<>();

    public BookIndividualReductionService(
            BookIndividualGraphRepository bookIndividualRepository,
            BookGraphRepository bookGraphRepository,
            Neo4jClient neo4jClient
    ) {
        this.bookIndividualRepository = bookIndividualRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.neo4jClient = neo4jClient;
    }

    @Transactional(readOnly = true)
    public boolean bookExists(UUID bookId) {
        return bookId != null && bookGraphRepository.findById(bookId).isPresent();
    }

    @Transactional
    public BookIndividualResolutionResponse resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookIndividualResolutionResponse(null, false, 0, 0, "Book ID is required");
        }

        ReentrantLock lock = bookLocks.computeIfAbsent(bookId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return resolveBookLocked(bookId);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                bookLocks.remove(bookId, lock);
            }
        }
    }

    private BookIndividualResolutionResponse resolveBookLocked(UUID bookId) {

        List<BookReductionCandidate> candidates = findReductionCandidates(bookId);
        if (candidates.isEmpty()) {
            return new BookIndividualResolutionResponse(bookId, false, 0, 0, "No chapter individuals found for book");
        }

        bookIndividualRepository.deleteByBookId(bookId);

        List<BookIndividual> bookIndividuals = new ArrayList<>();
        for (BookReductionCandidate candidate : candidates) {
            if (candidate.getNormalizedName() == null || candidate.getNormalizedName().isBlank()) {
                continue;
            }
            bookIndividuals.add(new BookIndividual(
                    UUID.randomUUID(),
                    bookId,
                    candidate.getDisplayName(),
                    candidate.getNormalizedName(),
                    safeCount(bookIndividualRepository.countChapterIndividualsForBookAndName(bookId, candidate.getNormalizedName())),
                    candidate.getChapterIndividualId(),
                    candidate.getChapterId(),
                    null,
                    null
            ));
        }

        if (bookIndividuals.isEmpty()) {
            return new BookIndividualResolutionResponse(bookId, false, candidates.size(), 0, "No resolvable chapter individuals found for book");
        }

        List<BookIndividual> savedIndividuals = new ArrayList<>();
        bookIndividualRepository.saveAll(bookIndividuals).forEach(savedIndividuals::add);

        for (BookIndividual bookIndividual : savedIndividuals) {
            bookIndividualRepository.linkBookToIndividual(bookId, bookIndividual.id());
            bookIndividualRepository.linkChapterIndividualsForBookAndNameToBookIndividual(
                    bookId,
                    bookIndividual.normalizedName(),
                    bookIndividual.id()
            );
        }

        return new BookIndividualResolutionResponse(
                bookId,
                true,
                candidates.size(),
                safeCount(bookIndividualRepository.countBookIndividualsByBookId(bookId)),
                "Resolved book-level individuals"
        );
    }

    private int safeCount(long count) {
        return Math.toIntExact(count);
    }

    private List<BookReductionCandidate> findReductionCandidates(UUID bookId) {
        return neo4jClient.query("""
                MATCH (c:Chapter)-[:IN_BOOK]->(b:Book {id: $bookId})
                MATCH (c)-[:HAS_INDIVIDUAL]->(ci:ChapterIndividual)
                WHERE ci.normalizedName IS NOT NULL AND trim(ci.normalizedName) <> ''
                WITH ci, c
                ORDER BY ci.normalizedName, coalesce(ci.displayName, ''), c.chapterNumber, coalesce(ci.chapterId, c.id)
                WITH ci.normalizedName AS normalizedName, collect(DISTINCT ci) AS chapterIndividuals
                WITH normalizedName, chapterIndividuals, head(chapterIndividuals) AS representative
                RETURN representative.id AS chapterIndividualId,
                       representative.chapterId AS chapterId,
                       representative.displayName AS displayName,
                       normalizedName AS normalizedName
                ORDER BY normalizedName
                """)
                .bind(bookId.toString()).to("bookId")
                .fetch()
                .all()
                .stream()
                .map(this::toCandidate)
                .toList();
    }

    private BookReductionCandidate toCandidate(Map<String, Object> row) {
        return new BookReductionCandidate(
                toUuid(row.get("chapterIndividualId")),
                toUuid(row.get("chapterId")),
                (String) row.get("displayName"),
                (String) row.get("normalizedName")
        );
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    @Getter
    @RequiredArgsConstructor
    private static final class BookReductionCandidate {
        private final UUID chapterIndividualId;
        private final UUID chapterId;
        private final String displayName;
        private final String normalizedName;
    }
}
