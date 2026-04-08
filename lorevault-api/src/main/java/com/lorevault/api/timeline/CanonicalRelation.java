package com.lorevault.api.timeline;

/**
 * Canonical subset of Allen interval relations used in LoreVault timeline edges.
 * These represent the 7 primary relations with forward orientation.
 */
public enum CanonicalRelation {
    BEFORE,
    MEETS,
    OVERLAPS,
    STARTS,
    DURING,
    FINISHES,
    EQUALS
}
