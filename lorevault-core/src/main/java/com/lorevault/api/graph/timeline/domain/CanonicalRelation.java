package com.lorevault.api.graph.timeline.domain;

/**
 * Canonical subset of Allen interval relations used in LoreVault timeline edges.
 * These represent the 7 primary relations with forward orientation.
 */
public enum CanonicalRelation {
    // LoreVault's canonical subset of Allen interval relations.
    // MEETS is normalised to BEFORE; EQUALS is normalised to OVERLAPS.
    // Both enum values remain for completeness but are not canonical output.
    BEFORE,
    MEETS,     // acknowledged — normalised to BEFORE
    OVERLAPS,
    STARTS,
    DURING,
    FINISHES,
    EQUALS     // acknowledged — normalised to OVERLAPS
}
