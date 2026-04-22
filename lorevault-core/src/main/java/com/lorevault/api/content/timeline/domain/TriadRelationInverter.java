package com.lorevault.api.content.timeline.domain;

/**
 * Invert relation expressed as prev -> curr into curr vs prev label string.
 * Maps the practical inferred relations used by LoreVault.
 */
public final class TriadRelationInverter {
    private TriadRelationInverter() {}

    /**
     * @param prevToCurr one of R:temporal.before|meets|overlaps|contains
     * @return inverted label for curr vs prev (e.g., before -> R:temporal.after)
     */
    public static String invertPrevToCurr(String prevToCurr) {
        if (prevToCurr == null) return null;
        String key = prevToCurr.trim().toLowerCase();
        // Normalize to base token (after 'R:temporal.') for robustness
        String base = key.replace("r:temporal.", "");
        return switch (base) {
            case "before" -> "R:temporal.after";
            case "after" -> "R:temporal.before";
            case "meets" -> "R:temporal.after";
            case "met_by" -> "R:temporal.before";
            case "overlaps" -> "R:temporal.overlapped_by";
            case "contains" -> "R:temporal.during";
            case "during" -> "R:temporal.contains";
            default -> null;
        };
    }
}
