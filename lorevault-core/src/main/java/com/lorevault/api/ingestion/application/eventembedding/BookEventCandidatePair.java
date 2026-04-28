package com.lorevault.api.ingestion.application.eventembedding;

import java.util.UUID;

/**
 * Immutable pair of ChapterEvent IDs that are ANN-similar within the same chapter.
 *
 * <p>The pair key is stable and unordered: {@code eventId1} is always the lexicographically
 * smaller UUID string, ensuring that {@code (A, B)} and {@code (B, A)} collapse to the same
 * pair.  When deduplicating across multiple ANN queries the pair with the highest
 * {@code annScore} is retained.
 *
 * @param eventId1   the smaller UUID (by string comparison) of the two events
 * @param eventId2   the larger UUID (by string comparison) of the two events
 * @param annScore   cosine similarity from the ANN index query
 */
public record BookEventCandidatePair(
        UUID eventId1,
        UUID eventId2,
        double annScore
) {
    /**
     * Factory that sorts the two IDs into canonical order so that pair identity is
     * independent of which event was the query event.
     */
    public static BookEventCandidatePair of(UUID a, UUID b, double score) {
        if (a.toString().compareTo(b.toString()) <= 0) {
            return new BookEventCandidatePair(a, b, score);
        } else {
            return new BookEventCandidatePair(b, a, score);
        }
    }
}
