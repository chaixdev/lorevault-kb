package com.lorevault.api.ingestion.resolution.location;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted work-claim node that serialises concurrent book-reduction runs
 * across multiple JVM instances.
 *
 * <p>A worker atomically creates this node (via a MERGE-on-CREATE Cypher query)
 * before starting a book reduction pass.  The unique constraint on {@code bookId}
 * ensures only one worker at a time can hold the claim for a given book.
 * After the reduction completes (or fails), the worker deletes the node.
 *
 * <p>Stale claims (created before a configurable threshold) are forcibly released
 * by {@link BookReductionClaimService}
 * to handle worker crashes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("BookReductionClaim")
public class BookReductionClaim {

    /** The book whose reduction is being serialised — acts as the unique constraint key. */
    @Id
    private UUID bookId;

    /** When this claim was acquired. */
    private LocalDateTime claimedAt;

    /** Identifier of the worker that holds this claim (hostname + thread, for diagnostics). */
    private String workerId;
}
