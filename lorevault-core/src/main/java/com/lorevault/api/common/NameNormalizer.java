package com.lorevault.api.common;

/**
 * Shared name normalization for entity-claim matching.
 * Used by both entity persistence (map keys) and claim persistence (lookup keys).
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    /**
     * Normalize a name for consistent lookup: trim, collapse whitespace,
     * lowercase, strip punctuation. Covers LLM punctuation drift
     * (e.g., "Mr. Underhill" → "mr underhill").
     *
     * @param name the raw name (may be null)
     * @return normalized name, or null if input is null or blank after trimming
     */
    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("\\s+", " ")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", "");
    }
}
