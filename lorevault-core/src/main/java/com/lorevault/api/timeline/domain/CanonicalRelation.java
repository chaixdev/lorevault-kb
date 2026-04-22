package com.lorevault.api.timeline.domain;

/**
 * Canonical subset of Allen interval relations used in LoreVault timeline edges.
 * These represent the 7 primary relations with forward orientation.
 */
public enum CanonicalRelation {
    BEFORE,
    @Deprecated(since = "April 2026", forRemoval = false)
    MEETS,
    OVERLAPS,
    STARTS,
    DURING,
    FINISHES,
    @Deprecated(since = "April 2026", forRemoval = false)
    EQUALS
}
