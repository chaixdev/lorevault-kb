package com.lorevault.api.domain.timeline;

public enum TemporalRelation {
    // Canonical forward relations
    BEFORE,
    MEETS,
    OVERLAPS,
    DURING,
    STARTS,
    FINISHES,
    EQUALS,

    // Inverse relations for normalization convenience (complete Allen 13)
    AFTER,
    MET_BY,
    OVERLAPPED_BY,
    STARTED_BY,
    CONTAINS,
    FINISHED_BY
}
