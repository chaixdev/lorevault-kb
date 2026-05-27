package com.lorevault.api.ingestion.resolution.consolidation;

/**
 * Shared utility for collapsing optional string fields during entity merging.
 *
 * <p>When multiple source entities contribute heterogeneous optional fields
 * (type, material, purpose, description, etc.), the first non-blank value
 * encountered is the winner. This replaces 4 identical copies across
 * Object and Collective chapter/book services.
 */
public final class PickFirstNonBlank {

    private PickFirstNonBlank() {
        // static utility
    }

    /**
     * Return the first non-blank value, preferring {@code current} over
     * {@code candidate}.
     *
     * @param current   the current accumulated value (may be null)
     * @param candidate the incoming candidate value (may be null)
     * @return {@code current} if it is non-blank, otherwise
     *         {@code candidate} if it is non-blank, otherwise null
     */
    public static String pick(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return null;
    }
}
