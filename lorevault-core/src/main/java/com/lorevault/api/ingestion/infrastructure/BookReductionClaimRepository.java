package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ingestion.domain.BookReductionClaim;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for {@link BookReductionClaim} nodes.
 *
 * <p>The key operation is {@link #tryAcquireClaim}, which uses a MERGE-on-CREATE
 * Cypher statement to atomically claim a book.  A second MATCH on the {@code workerId}
 * confirms whether this call was the one that created the node — only that call
 * returns {@code true}.
 */
public interface BookReductionClaimRepository extends Neo4jRepository<BookReductionClaim, UUID> {

    /**
     * Atomically try to create a {@link BookReductionClaim} for {@code bookId}.
     *
     * <p>Uses {@code MERGE} with {@code ON CREATE SET} so that at most one writer
     * acquires the claim even under concurrent requests.  Returns {@code true} when
     * this invocation created the node (i.e. the claim was not already held).
     *
     * @param bookId    the book to claim
     * @param claimedAt timestamp for the new claim
     * @param workerId  diagnostic identifier of the claiming worker
     * @return {@code true} if this call acquired the claim
     */
    @Query("""
            MERGE (c:BookReductionClaim {bookId: $bookId})
            ON CREATE SET c.claimedAt = $claimedAt, c.workerId = $workerId
            RETURN c.workerId = $workerId AS acquired
            """)
    boolean tryAcquireClaim(
            @Param("bookId") String bookId,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("workerId") String workerId
    );

    /**
     * Release (delete) a claim held by a specific worker.
     * No-op when the claim does not exist or is held by a different worker.
     */
    @Query("MATCH (c:BookReductionClaim {bookId: $bookId, workerId: $workerId}) DELETE c")
    void releaseClaim(@Param("bookId") String bookId, @Param("workerId") String workerId);

    /**
     * Force-release all claims created before {@code threshold}.
     * Used on startup and by a watchdog to recover from worker crashes.
     */
    @Query("MATCH (c:BookReductionClaim) WHERE c.claimedAt < $threshold DELETE c")
    void deleteStaleClaimsOlderThan(@Param("threshold") LocalDateTime threshold);
}
