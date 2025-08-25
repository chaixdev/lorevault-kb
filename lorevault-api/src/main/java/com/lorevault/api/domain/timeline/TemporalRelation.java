package com.lorevault.api.domain.timeline;

public enum TemporalRelation {
    BEFORE,
    MEETS,
    MET_BY,
    OVERLAPS,
    OVERLAPPED_BY,
    DURING,
    CONTAINS,
    STARTS,
    STARTED_BY,
    FINISHES,
    FINISHED_BY,
    EQUALS,
    AFTER  // Keep for bidirectional convenience
}
