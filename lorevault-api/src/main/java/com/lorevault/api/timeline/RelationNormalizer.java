package com.lorevault.api.timeline;

import java.util.Objects;

/**
 * Utility to normalize Allen interval relations to LoreVault's practical canonical subset with forward orientation.
 *
 * "Forward" orientation means (A -> B) expresses the returned canonical relation from subject A to object B.
 * If the input relation denotes the inverse direction (e.g., AFTER), the normalizer flips orientation
 * and returns the canonical counterpart (e.g., BEFORE) with swapped endpoints.
 */
public final class RelationNormalizer {

    public record Normalized(CanonicalRelation relation, boolean flipped) {}

    private RelationNormalizer() {}

    /**
     * Normalize the given temporal relation to a canonical relation.
     *
     * @param relation Allen relation (possibly inverse)
     * @return Normalized canonical relation and whether orientation should be flipped (swap endpoints)
     */
    public static Normalized normalize(TemporalRelation relation) {
        Objects.requireNonNull(relation, "relation");
        switch (relation) {
            case BEFORE:      return new Normalized(CanonicalRelation.BEFORE, false);
            case AFTER:       return new Normalized(CanonicalRelation.BEFORE, true);  // flip

            case MEETS:       return new Normalized(CanonicalRelation.BEFORE, false);
            case MET_BY:      return new Normalized(CanonicalRelation.BEFORE, true);

            case OVERLAPS:    return new Normalized(CanonicalRelation.OVERLAPS, false);
            case OVERLAPPED_BY:return new Normalized(CanonicalRelation.OVERLAPS, true);

            case STARTS:      return new Normalized(CanonicalRelation.STARTS, false);
            case STARTED_BY:  return new Normalized(CanonicalRelation.STARTS, true);

            case DURING:      return new Normalized(CanonicalRelation.DURING, false);
            case CONTAINS:    return new Normalized(CanonicalRelation.DURING, true);

            case FINISHES:    return new Normalized(CanonicalRelation.FINISHES, false);
            case FINISHED_BY: return new Normalized(CanonicalRelation.FINISHES, true);

            case EQUALS:      return new Normalized(CanonicalRelation.EQUALS, false);
            default:
                // Should never happen if enum is exhaustive
                throw new IllegalArgumentException("Unsupported temporal relation: " + relation);
        }
    }
}
