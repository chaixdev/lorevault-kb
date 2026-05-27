package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.content.association.BookIndividual;
import com.lorevault.api.content.association.BookIndividualGraphRepository;
import com.lorevault.api.library.book.BookGraphRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.neo4j.driver.exceptions.TransientException;

@Service
public class BookIndividualReductionService {

    private final BookIndividualGraphRepository bookIndividualRepository;
    private final BookGraphRepository bookGraphRepository;
    private final Neo4jClient neo4jClient;
    private final BookIndividualPersistenceService bookIndividualPersistenceService;

    public BookIndividualReductionService(
            BookIndividualGraphRepository bookIndividualRepository,
            BookGraphRepository bookGraphRepository,
            Neo4jClient neo4jClient,
            BookIndividualPersistenceService bookIndividualPersistenceService
    ) {
        this.bookIndividualRepository = bookIndividualRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.neo4jClient = neo4jClient;
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

        List<BookReductionCandidate> candidates = findReductionCandidates(bookId);
        if (candidates.isEmpty()) {
            bookIndividualPersistenceService.replaceBookIndividuals(bookId, List.of());
            return new BookIndividualResolutionResult(bookId, true, 0, 0, "No chapter individuals found for book");
        }
        return resolveBook(bookId, candidates);
    }

    BookIndividualResolutionResult resolveBook(UUID bookId, List<BookReductionCandidate> candidates) {

        List<BookIndividual> bookIndividuals = new ArrayList<>();
        for (BookReductionCandidate candidate : candidates) {
            if (candidate.normalizedName() == null || candidate.normalizedName().isBlank()) {
                continue;
            }
            bookIndividuals.add(new BookIndividual(
                    UUID.randomUUID(),
                    bookId,
                    candidate.displayName(),
                    candidate.normalizedName(),
                    safeCount(bookIndividualRepository.countChapterIndividualsForBookAndName(bookId, candidate.normalizedName())),
                    candidate.chapterIndividualId(),
                    candidate.chapterId(),
                    null,
                    null
            ));
        }

        if (bookIndividuals.isEmpty()) {
            return new BookIndividualResolutionResult(bookId, false, candidates.size(), 0, "No resolvable chapter individuals found for book");
        }

        bookIndividualPersistenceService.replaceBookIndividuals(bookId, bookIndividuals);

        return new BookIndividualResolutionResult(
                bookId,
                true,
                candidates.size(),
                safeCount(bookIndividualPersistenceService.countByBookId(bookId)),
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
                .bind(bookId != null ? bookId.toString() : null).to("bookId")
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

    private record BookReductionCandidate(
        UUID chapterIndividualId,
        UUID chapterId,
        String displayName,
        String normalizedName
    ) {}
}
