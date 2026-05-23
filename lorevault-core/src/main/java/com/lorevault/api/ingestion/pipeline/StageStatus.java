package com.lorevault.api.ingestion.pipeline;

/**
 * Lifecycle status of a pipeline stage.
 *
 * <p>Transitions are enforced by the coordinator, not by handlers. Handlers
 * use {@code setRunningConditionally} (atomic CAS from TRIGGERED to RUNNING)
 * and emit {@code StageCompletedEvent} — the coordinator writes COMPLETED,
 * SKIPPED, or FAILED.
 */
public enum StageStatus {

    /** Stage exists in graph, waiting for its trigger condition. */
    PENDING,

    /** Coordinator emitted {@code StageTriggered}. Handler has been (or will be) invoked. */
    TRIGGERED,

    /** Handler acknowledged and is executing work. */
    RUNNING,

    /** Handler completed successfully. StageOutput created. */
    COMPLETED,

    /** Idempotency check found existing StageOutput — work already done. */
    SKIPPED,

    /** Unrecoverable error. Handler emitted failure. */
    FAILED;

    /**
     * Returns true for terminal statuses that block downstream barrier resolution.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == FAILED;
    }

    /**
     * Returns true for statuses that satisfy a fan-in barrier condition.
     * Barriers require all parents to be COMPLETED or SKIPPED. FAILED prevents resolution.
     */
    public boolean satisfiesBarrier() {
        return this == COMPLETED || this == SKIPPED;
    }
}
