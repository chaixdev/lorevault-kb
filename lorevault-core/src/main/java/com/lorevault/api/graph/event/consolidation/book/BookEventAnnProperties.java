package com.lorevault.api.graph.event.consolidation.book;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning knobs for the per-chapter ANN candidate generation pass (Stage 4).
 *
 * <p>All thresholds are externally configurable via {@code lorevault.event-ann.*}.
 * Defaults match the values agreed in the Stage 4 design review.
 */
@ConfigurationProperties(prefix = "lorevault.event-ann")
public record BookEventAnnProperties(
        /**
         * Number of nearest neighbours to request from the vector index per query event.
         * Actual candidates are further filtered by {@code annFloor}.
         */
        int topK,

        /**
         * Multiplier applied to {@code topK} when querying the vector index so that
         * filtered-out nodes don't leave the result set thin.
         */
        int oversampleFactor,

        /**
         * Minimum cosine similarity for a pair to be considered a candidate at all.
         * Pairs below this score are discarded.
         */
        double annFloor,

        /**
         * Maximum number of candidate neighbours retained per source event after dedup.
         * Prevents a single event with many near-duplicates from dominating Stage 5.
         */
        int maxCandidatesPerEvent,

        /**
         * Expected embedding vector dimension.  Must match {@code lorevault.embedding.model.dimensions}.
         * Source vectors that do not match this dimension are skipped rather than sent to the vector
         * index, preventing dimension-mismatch errors from the Neo4j ANN call.
         */
        int embeddingDimension
) {
    public BookEventAnnProperties {
        if (topK <= 0) throw new IllegalArgumentException("topK must be positive");
        if (oversampleFactor <= 0) throw new IllegalArgumentException("oversampleFactor must be positive");
        if (annFloor < 0.0 || annFloor > 1.0) throw new IllegalArgumentException("annFloor must be in [0, 1]");
        if (maxCandidatesPerEvent <= 0) throw new IllegalArgumentException("maxCandidatesPerEvent must be positive");
        if (embeddingDimension <= 0) throw new IllegalArgumentException("embeddingDimension must be positive");
    }
}
