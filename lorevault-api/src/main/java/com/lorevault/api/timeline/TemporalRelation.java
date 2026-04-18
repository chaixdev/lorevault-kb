package com.lorevault.api.timeline;

public enum TemporalRelation {
    BEFORE,
    @Deprecated(since = "April 2026", forRemoval = false)
    MEETS,
    @Deprecated(since = "April 2026", forRemoval = false)
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
