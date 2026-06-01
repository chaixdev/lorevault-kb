package com.lorevault.api.orchestration.consolidation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted work-claim node that serialises concurrent book-reduction runs
 * across multiple JVM instances.
 *
 * <p>A worker atomically creates this node (via a MERGE-on-CREATE Cypher query)
 * before starting a book reduction pass.  The claim key includes both {@code bookId}
 * and {@code lane}, so separate entity lanes for the same book can reduce in parallel
 * while same-lane reductions remain serialized.
 * After the reduction completes (or fails), the worker deletes the node.
 *
 * <p>Stale claims (created before a configurable threshold) are forcibly released
 * by {@link BookConsolidationClaimService}
 * to handle worker crashes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Node("BookConsolidationClaim")
public class BookConsolidationClaim {

    /** Stable claim key, composed from book ID and lane. */
    @Id
    private String id;

    /** The book whose lane reduction is being serialised. */
    private UUID bookId;

    /** Entity lane/stage being reduced, e.g. BOOK_INDIVIDUAL_CONSOLIDATION. */
    private String lane;

    /** When this claim was acquired. */
    private LocalDateTime claimedAt;

    /** Identifier of the worker that holds this claim (hostname + thread, for diagnostics). */
    private String workerId;

    /** Per-attempt token used to distinguish a newly-created claim from an existing same-worker claim. */
    private String acquiredToken;
}
