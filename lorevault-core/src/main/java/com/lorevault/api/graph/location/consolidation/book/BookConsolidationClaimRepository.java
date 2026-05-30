package com.lorevault.api.graph.location.consolidation.book;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for {@link BookConsolidationClaim} nodes.
 *
 * <p>The key operation is {@link #tryAcquireClaim}, which uses a MERGE-on-CREATE
 * Cypher statement to atomically claim a book lane.  A per-attempt token confirms whether
 * this call was the one that created the node — only that call returns {@code true}.
 */
public interface BookConsolidationClaimRepository extends Neo4jRepository<BookConsolidationClaim, String> {

    /**
     * Atomically try to create a {@link BookConsolidationClaim} for {@code bookId}/{@code lane}.
     *
     * <p>Uses {@code MERGE} with {@code ON CREATE SET} so that at most one writer
     * acquires the claim even under concurrent requests.  Returns {@code true} when
     * this invocation created the node (i.e. the claim was not already held).
     *
     * @param claimId   stable claim identifier derived from book and lane
     * @param bookId    the book to claim
     * @param lane      the book reduction lane to claim
     * @param claimedAt timestamp for the new claim
     * @param workerId  diagnostic identifier of the claiming worker
     * @param acquiredToken per-attempt token used to prove this call created the claim
     * @return {@code true} if this call acquired the claim
     */
    @Query("""
            MERGE (c:BookConsolidationClaim {id: $claimId})
            ON CREATE SET c.bookId = $bookId,
                          c.lane = $lane,
                          c.claimedAt = $claimedAt,
                          c.workerId = $workerId,
                          c.acquiredToken = $acquiredToken,
                          c.stageId = $stageId
            RETURN c.acquiredToken = $acquiredToken AS acquired
            """)
    boolean tryAcquireClaim(
            @Param("claimId") String claimId,
            @Param("bookId") String bookId,
            @Param("lane") String lane,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("workerId") String workerId,
            @Param("acquiredToken") String acquiredToken,
            @Param("stageId") UUID stageId
    );

    /**
     * Release (delete) a claim held by a specific worker.
     * No-op when the claim does not exist or is held by a different worker.
     */
    @Query("MATCH (c:BookConsolidationClaim {id: $claimId, acquiredToken: $acquiredToken}) DELETE c")
    void releaseClaim(@Param("claimId") String claimId, @Param("acquiredToken") String acquiredToken);

    /**
     * Force-release all claims created before {@code threshold}.
     * Used on startup and by a watchdog to recover from worker crashes.
     */
    @Query("MATCH (c:BookConsolidationClaim) WHERE c.claimedAt < $threshold DELETE c")
    void deleteStaleClaimsOlderThan(@Param("threshold") LocalDateTime threshold);

    /**
     * Remove legacy or malformed claim rows that cannot safely participate in lane-scoped locking.
     */
    @Query("MATCH (c:BookConsolidationClaim) WHERE c.id IS NULL OR c.claimedAt IS NULL DELETE c")
    void deleteClaimsWithMissingKeyOrTimestamp();
}
