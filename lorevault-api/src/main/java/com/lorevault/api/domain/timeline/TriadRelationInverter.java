package com.lorevault.api.domain.timeline;

/**
 * Invert relation expressed as prev -> curr into curr vs prev label string.
 * Maps the 5 canonical relations per LV-085-0.
 */
public final class TriadRelationInverter {
    private TriadRelationInverter() {}

    /**
     * @param prevToCurr one of R:temporal.before|meets|overlaps|contains|equals
     * @return inverted label for curr vs prev (e.g., before -> R:temporal.after)
     */
    public static String invertPrevToCurr(String prevToCurr) {
        if (prevToCurr == null) return null;
        String key = prevToCurr.trim().toLowerCase();
        // Normalize to base token (after 'R:temporal.') for robustness
        String base = key.replace("r:temporal.", "");
        return switch (base) {
            case "before" -> "R:temporal.after";
            case "meets" -> "R:temporal.met_by";
            case "overlaps" -> "R:temporal.overlapped_by";
            case "contains" -> "R:temporal.during";
            case "equals" -> "R:temporal.equals";
            default -> null;
        };
    }
}
