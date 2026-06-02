package com.lorevault.api.orchestration.consolidation;

/**
 * Field-merging utility for entity consolidation.
 *
 * <p>Returns the first non-blank value when merging fields across
 * cluster members. Replaces duplicated {@code pickFirstNonBlank} methods
 * in Object and Collective consolidation services.
 */
public final class PickFirstNonBlank {

    private PickFirstNonBlank() {}

    /**
     * Return the current value if non-blank, otherwise the candidate.
     *
     * @param current   the current field value (may be null or blank)
     * @param candidate  the candidate field value (may be null or blank)
     * @return the first non-blank value, or null if both are blank/null
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