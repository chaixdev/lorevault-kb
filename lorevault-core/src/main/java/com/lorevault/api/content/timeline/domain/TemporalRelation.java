package com.lorevault.api.content.timeline.domain;

public enum TemporalRelation {
    // All 13 Allen interval relations are listed for completeness.
    // LoreVault normalizes MEETS → BEFORE, MET_BY → BEFORE (flipped), and EQUALS → OVERLAPS.
    BEFORE,
    MEETS,    // acknowledged — normalized to BEFORE
    MET_BY,   // acknowledged — normalized to BEFORE (flipped)
    OVERLAPS,
    OVERLAPPED_BY,
    DURING,
    CONTAINS,
    STARTS,
    STARTED_BY,
    FINISHES,
    FINISHED_BY,
    EQUALS,   // acknowledged — normalized to OVERLAPS
    AFTER     // bidirectional convenience
}
