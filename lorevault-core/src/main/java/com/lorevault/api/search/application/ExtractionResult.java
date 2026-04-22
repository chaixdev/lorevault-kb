package com.lorevault.api.search.application;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Result of entity extraction from a query string.
 *
 * <p>Separates entities found via the known-name trie (Strategy A)
 * from noun phrases discovered via OpenNLP chunking (Strategy B).
 * {@link #allCandidates()} is the union used for re-ranking.</p>
 */
public record ExtractionResult(
        List<String> knownEntities,
        List<String> unknownNounPhrases
) {

    public static ExtractionResult empty() {
        return new ExtractionResult(List.of(), List.of());
    }

    public static ExtractionResult of(List<String> known, List<String> unknown) {
        return new ExtractionResult(
                List.copyOf(known),
                List.copyOf(unknown)
        );
    }

    /** Union of both lists, deduplicated, case-preserving. */
    public Set<String> allCandidates() {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        result.addAll(knownEntities);
        result.addAll(unknownNounPhrases);
        return result;
    }

    public boolean isEmpty() {
        return knownEntities.isEmpty() && unknownNounPhrases.isEmpty();
    }
}
