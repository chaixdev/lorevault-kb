package com.lorevault.api.orchestration.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A mutable node representing one vertex in the pipeline DAG.
 *
 * <p>Owned by {@code ChapterIngestionJob} via {@code [:HAS_STAGE]}.
 * One Stage node per {@code (jobId, step)}. Rerun deletes the old Stage node
 * and creates a fresh one in place — no historical Runs.
 *
 * <p>{@code [:TRIGGERS]} edges between Stage nodes define the DAG topology
 * at runtime. These edges are created when a job starts and recreated during
 * cascade invalidation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Node("Stage")
public class Stage {

    @Id
    private UUID id;

    /** Owning job id — denormalized for Cypher queries. */
    private UUID jobId;

    /** The DAG vertex this Stage represents. */
    private StageKey step;

    /** Current lifecycle status. */
    private StageStatus status;

    /** How many times this stage has been triggered (including the current attempt). */
    private int attemptCount;

    /** Error message from the last failed attempt (nullable). */
    private String errorMessage;

    /** Whether the last failure is retryable (nullable). */
    private Boolean errorRetryable;

    /** When the coordinator emitted {@code StageTriggered}. */
    private LocalDateTime triggeredAt;

    /** When the handler called {@code setRunning}. */
    private LocalDateTime startedAt;

    /** When the coordinator wrote COMPLETED/SKIPPED/FAILED. */
    private LocalDateTime completedAt;

    // ── DAG topology edges ──────────────────────────────────────────

    /**
     * Outgoing {@code [:TRIGGERS]} edges to downstream stages.
     * These edges mirror the static {@code StageDag} topology and are used
     * by the coordinator for fan-in evaluation via reverse traversal.
     */
    @Relationship(type = "TRIGGERS", direction = Relationship.Direction.OUTGOING)
    private java.util.List<Stage> triggers;

    // ── Helper factories ────────────────────────────────────────────

    public static Stage pending(UUID jobId, StageKey step) {
        return Stage.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .step(step)
                .status(StageStatus.PENDING)
                .attemptCount(0)
                .build();
    }
}
