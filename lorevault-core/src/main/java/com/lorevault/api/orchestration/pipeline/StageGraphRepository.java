package com.lorevault.api.orchestration.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;

/**
 * Graph operations for {@code Stage} nodes — the durable orchestration layer.
 *
 * <p>Uses {@code Neo4jClient} directly instead of SDN repository proxies because
 * the fan-in barrier evaluation, conditional state transitions, and edge rewiring
 * require parameterised Cypher with atomic conditional writes.
 */
@Repository
public class StageGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(StageGraphRepository.class);

    private final Neo4jClient neo4jClient;

    public StageGraphRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // ── Creation ────────────────────────────────────────────────────

    /**
     * Create a fresh Stage node and link it to the job. Returns the generated id.
     */
    public UUID create(UUID jobId, StageKey step, StageStatus status) {
        UUID id = UUID.randomUUID();
        neo4jClient.query("""
                CREATE (s:Stage {id: $id, jobId: $jobId, step: $step, status: $status, attemptCount: 0})
                WITH s
                MATCH (j:ChapterIngestionJob {id: $jobId})
                CREATE (j)-[:HAS_STAGE]->(s)
                RETURN s.id
                """)
                .bind(id.toString()).to("id")
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .bind(status.name()).to("status")
                .run();
        return id;
    }

    // ── Reads ───────────────────────────────────────────────────────

    public Optional<Stage> findByJobIdAndStep(UUID jobId, StageKey step) {
        return neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $step})
                RETURN s
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .fetchAs(Stage.class)
                .mappedBy((typeSystem, record) -> mapStageNode(record.get("s").asNode()))
                .one();
    }

    public List<Stage> findByJobId(UUID jobId) {
        return new ArrayList<>(neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId})
                RETURN s
                """)
                .bind(jobId.toString()).to("jobId")
                .fetchAs(Stage.class)
                .mappedBy((typeSystem, record) -> mapStageNode(record.get("s").asNode()))
                .all());
    }

    public Set<UUID> findStageIdsByJobAndSteps(UUID jobId, Set<StageKey> steps) {
        List<String> stepNames = steps.stream().map(StageKey::name).toList();
        return new HashSet<>(neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId})
                WHERE s.step IN $steps
                RETURN s.id
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(stepNames).to("steps")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) -> UUID.fromString(record.get("s.id").asString()))
                .all());
    }

    public UUID findStageId(UUID jobId, StageKey step) {
        return neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $step})
                RETURN s.id
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) -> UUID.fromString(record.get("s.id").asString()))
                .one()
                .orElse(null);
    }

    // ── Conditional state transitions ───────────────────────────────

    /**
     * Atomic CAS: set status to TRIGGERED only if currently PENDING AND all parents
     * are COMPLETED or SKIPPED. Returns true if this thread made the transition.
     */
    public boolean tryTrigger(UUID jobId, StageKey childStep) {
        return neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $childStep})
                WHERE s.status = 'PENDING'
                OPTIONAL MATCH (parent:Stage {jobId: $jobId})-[triggers:TRIGGERS]->(s)
                WITH s, collect(parent.status) AS statuses
                WHERE all(st IN statuses WHERE st IN ['COMPLETED', 'SKIPPED'])
                SET s.status = 'TRIGGERED', s.triggeredAt = datetime(), s.attemptCount = s.attemptCount + 1
                RETURN s.id
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(childStep.name()).to("childStep")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) -> UUID.fromString(record.get("s.id").asString()))
                .one()
                .isPresent();
    }

    /**
     * Atomic CAS: set status to RUNNING only if currently TRIGGERED.
     * Returns the Stage node ID if the transition succeeded, empty otherwise.
     */
    public Optional<UUID> setRunningConditionally(UUID jobId, StageKey step) {
        return neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $step})
                WHERE s.status = 'TRIGGERED'
                SET s.status = 'RUNNING', s.startedAt = datetime()
                RETURN s.id
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) -> UUID.fromString(record.get("s.id").asString()))
                .one();
    }

    public void setCompleted(UUID jobId, StageKey step) {
        neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $step})
                SET s.status = 'COMPLETED', s.completedAt = datetime()
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .run();
    }

    public void setSkipped(UUID jobId, StageKey step) {
        neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $step})
                SET s.status = 'SKIPPED', s.completedAt = datetime()
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .run();
    }

    public void setFailed(UUID jobId, StageKey step, String errorMessage, boolean retryable) {
        neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId, step: $step})
                SET s.status = 'FAILED',
                    s.completedAt = datetime(),
                    s.errorMessage = $errorMessage,
                    s.errorRetryable = $retryable
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(step.name()).to("step")
                .bind(errorMessage).to("errorMessage")
                .bind(retryable).to("retryable")
                .run();
    }

    // ── Recovery ────────────────────────────────────────────────────

    /**
     * Stages that are TRIGGERED but never completed (crash between write and publish).
     * Grace window prevents premature re-trigger.
     */
    public List<Stage> findStaleTriggered(Duration graceWindow) {
        return new ArrayList<>(neo4jClient.query("""
                MATCH (s:Stage)
                WHERE s.status = 'TRIGGERED'
                  AND s.triggeredAt < datetime() - duration($grace)
                  AND s.completedAt IS NULL
                RETURN s
                """)
                .bind(Map.of("seconds", graceWindow.toSeconds())).to("grace")
                .fetchAs(Stage.class)
                .mappedBy((typeSystem, record) -> mapStageNode(record.get("s").asNode()))
                .all());
    }

    /**
     * Stages stuck in RUNNING (handler crashed mid-execution).
     * Resets to TRIGGERED if attempts remain; FAILED if max attempts exhausted.
     */
    public List<Stage> findAndResetStaleRunning(Duration staleThreshold, int maxAttempts) {
        return new ArrayList<>(neo4jClient.query("""
                MATCH (s:Stage)
                WHERE s.status = 'RUNNING'
                   AND s.startedAt < datetime() - duration($threshold)
                OPTIONAL MATCH (parent:Stage)-[triggers:TRIGGERS]->(s)
                WITH s, collect(parent.status) AS statuses
                WHERE all(st IN statuses WHERE st IN ['COMPLETED', 'SKIPPED'])
                WITH s,
                     CASE WHEN s.attemptCount < $maxAttempts THEN 'TRIGGERED' ELSE 'FAILED' END AS target
                SET s.status = target,
                    s.attemptCount = s.attemptCount + 1,
                    s.triggeredAt = datetime()
                RETURN s
                """)
                .bind(Map.of("seconds", staleThreshold.toSeconds())).to("threshold")
                .bind(maxAttempts).to("maxAttempts")
                .fetchAs(Stage.class)
                .mappedBy((typeSystem, record) -> mapStageNode(record.get("s").asNode()))
                .all());
    }

    // ── Cascade invalidation ────────────────────────────────────────

    /** Bulk-delete Stage nodes for a set of step values. */
    public void deleteByJobIdAndStepIn(UUID jobId, Set<StageKey> steps) {
        List<String> stepNames = steps.stream().map(StageKey::name).toList();
        neo4jClient.query("""
                MATCH (s:Stage {jobId: $jobId})
                WHERE s.step IN $steps
                DETACH DELETE s
                """)
                .bind(jobId.toString()).to("jobId")
                .bind(stepNames).to("steps")
                .run();
    }

    /**
     * Rewire {@code [:TRIGGERS]} edges for the given stages based on the DAG.
     * Connects new Stage nodes to each other and to untouched sibling stages.
     */
    public void rewireEdges(UUID jobId, Map<StageKey, UUID> newStageIds, StageDag dag) {
        for (Map.Entry<StageKey, UUID> entry : newStageIds.entrySet()) {
            StageKey step = entry.getKey();
            UUID newId = entry.getValue();
            for (StageKey child : dag.childrenOf(step)) {
                UUID childId;
                if (newStageIds.containsKey(child)) {
                    childId = newStageIds.get(child);
                } else {
                    childId = findStageId(jobId, child);
                }
                if (childId != null) {
                    neo4jClient.query("""
                            MATCH (s:Stage {id: $id})
                            MATCH (c:Stage {id: $childId})
                            CREATE (s)-[:TRIGGERS]->(c)
                            """)
                            .bind(newId.toString()).to("id")
                            .bind(childId.toString()).to("childId")
                            .run();
                }
            }
        }
    }

    /**
     * Create all PENDING Stage nodes for a fresh job and wire DAG edges.
     */
    public Map<StageKey, UUID> createAllForJob(UUID jobId, StageDag dag) {
        Map<StageKey, UUID> ids = new LinkedHashMap<>();
        for (StageKey step : StageKey.values()) {
            UUID id = create(jobId, step, StageStatus.PENDING);
            ids.put(step, id);
        }
        rewireEdges(jobId, ids, dag);
        return ids;
    }

    // ── Mapping helper ──────────────────────────────────────────────

    private Stage mapStageNode(org.neo4j.driver.types.Node node) {
        return Stage.builder()
                .id(UUID.fromString(node.get("id").asString()))
                .jobId(UUID.fromString(node.get("jobId").asString()))
                .step(StageKey.valueOf(node.get("step").asString()))
                .status(StageStatus.valueOf(node.get("status").asString()))
                .attemptCount(node.get("attemptCount").asInt(0))
                .errorMessage(node.containsKey("errorMessage") && !node.get("errorMessage").isNull()
                        ? node.get("errorMessage").asString() : null)
                .errorRetryable(node.containsKey("errorRetryable") && !node.get("errorRetryable").isNull()
                        ? node.get("errorRetryable").asBoolean() : null)
                .triggeredAt(safeLocalDateTime(node, "triggeredAt"))
                .startedAt(safeLocalDateTime(node, "startedAt"))
                .completedAt(safeLocalDateTime(node, "completedAt"))
                .build();
    }

    /**
     * Safely reads a LocalDateTime from a Neo4j node property.
     * Tries {@link Value#asLocalDateTime()} first; falls back to
     * {@link Value#asZonedDateTime()}.toLocalDateTime() for values stored as
     * DATE_TIME with timezone offset by Spring Data Neo4j's {@code @CreatedDate}
     * or explicit {@code ZonedDateTime} writes.
     */
    private static java.time.LocalDateTime safeLocalDateTime(
            org.neo4j.driver.types.Node node, String key) {
        if (!node.containsKey(key) || node.get(key).isNull()) {
            return null;
        }
        org.neo4j.driver.Value value = node.get(key);
        try {
            return value.asLocalDateTime();
        } catch (org.neo4j.driver.exceptions.value.Uncoercible e) {
            return value.asZonedDateTime().toLocalDateTime();
        }
    }
}
