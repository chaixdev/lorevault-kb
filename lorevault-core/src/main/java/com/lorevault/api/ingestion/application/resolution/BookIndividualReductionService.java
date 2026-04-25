package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.content.entities.BookIndividual;
import com.lorevault.api.content.entities.BookIndividualGraphRepository;
import com.lorevault.api.library.infrastructure.BookGraphRepository;
import com.lorevault.api.ingestion.application.result.BookIndividualResolutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookIndividualReductionService {

    private final BookIndividualGraphRepository bookIndividualRepository;
    private final BookGraphRepository bookGraphRepository;
    private final Neo4jClient neo4jClient;
    private final BookReductionClaimService claimService;

    public BookIndividualReductionService(
            BookIndividualGraphRepository bookIndividualRepository,
            BookGraphRepository bookGraphRepository,
            Neo4jClient neo4jClient,
            BookReductionClaimService claimService
    ) {
        this.bookIndividualRepository = bookIndividualRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.neo4jClient = neo4jClient;
        this.claimService = claimService;
    }

    @Transactional(readOnly = true)
    public boolean bookExists(UUID bookId) {
        return bookId != null && bookGraphRepository.findById(bookId).isPresent();
    }

    public BookIndividualResolutionResult resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookIndividualResolutionResult(null, false, 0, 0, "Book ID is required");
        }

        if (!claimService.tryAcquireClaimWithRetry(bookId, 6, 500)) {
            return new BookIndividualResolutionResult(bookId, false, 0, 0, "Reduction already in progress for book");
        }
        try {
            return resolveBookLocked(bookId);
        } finally {
            // releaseClaim uses REQUIRES_NEW and runs outside the resolveBookLocked transaction,
            // so the claim is only released after the reduction work has committed.
            claimService.releaseClaim(bookId);
        }
    }

    @Transactional
    BookIndividualResolutionResult resolveBookLocked(UUID bookId) {

        List<BookReductionCandidate> candidates = findReductionCandidates(bookId);
        if (candidates.isEmpty()) {
            return new BookIndividualResolutionResult(bookId, false, 0, 0, "No chapter individuals found for book");
        }

        bookIndividualRepository.deleteByBookId(bookId);

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

        return new BookIndividualResolutionResult(
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

    private record BookReductionCandidate(
        UUID chapterIndividualId,
        UUID chapterId,
        String displayName,
        String normalizedName
    ) {}
}
