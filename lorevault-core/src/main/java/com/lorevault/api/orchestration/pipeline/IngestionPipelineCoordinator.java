package com.lorevault.api.orchestration.pipeline;

import com.lorevault.api.orchestration.signals.StageCompletedEvent;
import com.lorevault.api.orchestration.signals.StageTriggeredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.*;

/**
 * Durable ingestion pipeline coordinator — replaces the in-memory
 * {@code IngestionCompletionCoordinator}.
 *
 * <h3>Core loop</h3>
 * <pre>
 * StageCompleted received
 *   → write Stage status + StageOutput to Neo4j (durable)
 *   → for each child stage, evaluate fan-in via conditional Cypher
 *   → emit StageTriggered for any child whose barrier is now satisfied
 * </pre>
 *
 * <h3>Recovery</h3>
 * Two {@code @Scheduled} jobs handle crash-between-events:
 * <ul>
 *   <li>Stale TRIGGERED: stage was triggered but no handler ran (crash between write and publish)</li>
 *   <li>Stale RUNNING: handler crashed mid-execution — reset to TRIGGERED or FAILED</li>
 * </ul>
 */
@Slf4j
@Component
public class IngestionPipelineCoordinator {

    private final StageDag dag;
    private final StageGraphRepository stageRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final Neo4jClient neo4jClient;

    @Value("${lorevault.ingestion.stale-trigger-grace-seconds:60}")
    private long staleTriggerGraceSeconds;

    @Value("${lorevault.ingestion.stale-running-threshold-seconds:300}")
    private long staleRunningThresholdSeconds;

    @Value("${lorevault.ingestion.max-stage-attempts:3}")
    private int maxStageAttempts;

    @Autowired
    public IngestionPipelineCoordinator(
            StageGraphRepository stageRepo,
            ApplicationEventPublisher eventPublisher,
            Neo4jClient neo4jClient) {
        this.dag = new StageDag();
        this.stageRepo = stageRepo;
        this.eventPublisher = eventPublisher;
        this.neo4jClient = neo4jClient;
    }

    /**
     * Test constructor — allows direct injection of @Value fields.
     */
    IngestionPipelineCoordinator(
            StageGraphRepository stageRepo,
            ApplicationEventPublisher eventPublisher,
            Neo4jClient neo4jClient,
            long staleTriggerGraceSeconds,
            long staleRunningThresholdSeconds,
            int maxStageAttempts) {
        this.dag = new StageDag();
        this.stageRepo = stageRepo;
        this.eventPublisher = eventPublisher;
        this.neo4jClient = neo4jClient;
        this.staleTriggerGraceSeconds = staleTriggerGraceSeconds;
        this.staleRunningThresholdSeconds = staleRunningThresholdSeconds;
        this.maxStageAttempts = maxStageAttempts;
    }

    // ── Event-driven orchestration ──────────────────────────────────

    /**
     * Receives completion from any handler. Writes durable state, creates
     * StageOutput, evaluates DAG transitions. Uses {@code AFTER_COMMIT} so
     * handlers can safely use their own transaction before the coordinator reads.
     */
    @EventListener
    public void onStageCompleted(StageCompletedEvent event) {
        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();
        StageKey stage = event.getStage();
        StageResult result = event.getResult();

        if (result.success()) {
            stageRepo.setCompleted(jobId, stage);

            log.info("[ORCHESTRATION] Stage completed: jobId={} chapterId={} stage={} summary={} counts={}",
                    jobId, chapterId, stage, result.summary(), result.counts());

            evaluateDownstream(jobId, chapterId, bookId, stage);
        } else {
            stageRepo.setFailed(jobId, stage, result.summary(), result.retryable());
            log.warn("[ORCHESTRATION] Stage failed: jobId={} chapterId={} stage={} summary={} retryable={}",
                    jobId, chapterId, stage, result.summary(), result.retryable());

            // FAILED blocks downstream barriers — no evaluateDownstream needed
        }
    }

    /**
     * For each child of the completed stage, evaluate the fan-in barrier.
     * If all parents are COMPLETED or SKIPPED, atomically transition the
     * child to TRIGGERED and publish the event.
     */
    private void evaluateDownstream(UUID jobId, UUID chapterId, UUID bookId, StageKey completedStage) {
        for (StageKey child : dag.childrenOf(completedStage)) {
            try {
                boolean triggered = stageRepo.tryTrigger(jobId, child);
                if (triggered) {
                    UUID resolvedBookId = resolveBookId(chapterId, bookId, child);
                    log.info("[ORCHESTRATION] Barrier open — triggering: jobId={} chapterId={} child={}",
                            jobId, chapterId, child);
                    eventPublisher.publishEvent(
                            new StageTriggeredEvent(this, jobId, chapterId, resolvedBookId, child));
                }
            } catch (Exception e) {
                log.error("[ORCHESTRATION] Failed to evaluate barrier for child={} after parent={}: {}",
                        child, completedStage, e.getMessage(), e);
            }
        }
    }

    // ── Recovery ────────────────────────────────────────────────────

    /**
     * Re-publishes StageTriggered for stages that were TRIGGERED in Neo4j
     * but where the event was never published (crash between write and publish).
     * Runs every 30s with a configurable grace window.
     */
    @Scheduled(fixedDelay = 30_000)
    public void recoverStaleTriggers() {
        Duration graceWindow = Duration.ofSeconds(staleTriggerGraceSeconds);
        List<Stage> stale = stageRepo.findStaleTriggered(graceWindow);
        for (Stage s : stale) {
            UUID chapterId = findChapterId(s.getJobId());
            UUID bookId = resolveBookId(chapterId, null, s.getStep());
            log.warn("[ORCHESTRATION] Re-publishing stale trigger: jobId={} step={} triggeredAt={}",
                    s.getJobId(), s.getStep(), s.getTriggeredAt());
            eventPublisher.publishEvent(
                    new StageTriggeredEvent(this, s.getJobId(), chapterId, bookId, s.getStep()));
        }
    }

    /**
     * Resets stalled RUNNING stages. Sets to TRIGGERED (retry) or FAILED
     * (max attempts exhausted). Publishes StageTriggered for retried stages.
     */
    @Scheduled(fixedDelay = 30_000)
    public void recoverStaleRunning() {
        Duration threshold = Duration.ofSeconds(staleRunningThresholdSeconds);
        List<Stage> stale = stageRepo.findAndResetStaleRunning(threshold, maxStageAttempts);
        for (Stage s : stale) {
            UUID chapterId = findChapterId(s.getJobId());
            UUID bookId = resolveBookId(chapterId, null, s.getStep());
            if (s.getStatus() == StageStatus.TRIGGERED) {
                log.warn("[ORCHESTRATION] Resetting stale RUNNING→TRIGGERED: jobId={} step={} attempt={}/{}",
                        s.getJobId(), s.getStep(), s.getAttemptCount(), maxStageAttempts);
                eventPublisher.publishEvent(
                        new StageTriggeredEvent(this, s.getJobId(), chapterId, bookId, s.getStep()));
            } else {
                log.error("[ORCHESTRATION] Stale RUNNING→FAILED (max attempts): jobId={} step={} attempts={}",
                        s.getJobId(), s.getStep(), s.getAttemptCount());
            }
        }
    }

    // ── Fresh job bootstrap ─────────────────────────────────────────

    /**
     * Called after creating a ChapterIngestionJob.
     * Creates all PENDING Stage nodes, wires DAG edges, and emits
     * StageTriggered for root stages.
     */
    public void bootstrapJob(UUID jobId, UUID chapterId) {
        log.info("[ORCHESTRATION] Bootstrapping job: jobId={} chapterId={}", jobId, chapterId);
        Map<StageKey, UUID> stageIds = stageRepo.createAllForJob(jobId, dag);

        // Validate DAG connectivity
        Set<StageKey> orphans = dag.validateConnectivity();
        if (!orphans.isEmpty()) {
            log.error("[ORCHESTRATION] DAG connectivity violation — unreachable stages: {}", orphans);
        }

        // Emit triggers for root stages
        for (StageKey root : dag.roots()) {
            boolean triggered = stageRepo.tryTrigger(jobId, root);
            if (triggered) {
                log.info("[ORCHESTRATION] Root triggered: jobId={} stage={}", jobId, root);
                eventPublisher.publishEvent(
                        new StageTriggeredEvent(this, jobId, chapterId, root));
            }
        }
    }

    // ── Manual rerun with cascade invalidation ──────────────────────

    /**
     * Find the ingestion job ID for a chapter.
     */
    public UUID findJobIdByChapterId(UUID chapterId) {
        return findJobId(chapterId);
    }

    /**
     * Find the book ID for a chapter.
     */
    public UUID findBookIdByChapterId(UUID chapterId) {
        return findBookId(chapterId);
    }

    /**
     * Rerun a stage by invalidating it and all its transitive downstream stages.
     * Sibling branches are untouched. The rerun stage is re-triggered.
     *
     * <p>In a single transaction: deletes graph data (deepest first),
     * StageOutputs, and Stage nodes for the rerun path, then recreates
     * fresh PENDING nodes and rewires DAG edges.
     */
    @Transactional
    public void rerunStage(UUID jobId, UUID chapterId, UUID bookId, StageKey stage) {
        log.info("[ORCHESTRATION] Rerunning stage: jobId={} chapterId={} stage={}", jobId, chapterId, stage);

        // 1. The rerun path — only stages downstream of the rerun point
        Set<StageKey> invalidated = dag.transitiveDownstream(stage);

        // 2. Collect existing stageIds for graph data cleanup
        Set<UUID> invalidatedStageIds = stageRepo.findStageIdsByJobAndSteps(jobId, invalidated);

        // 3. Delete graph data, deepest first (children before parents)
        List<StageKey> byDepth = dag.topologicalDepthDescending(invalidated);
        for (StageKey s : byDepth) {
            UUID stageId = stageRepo.findStageId(jobId, s);
            if (stageId != null) {
                deleteDataByStageId(stageId);
            }
        }

        // 4. Delete stale Stage nodes
        stageRepo.deleteByJobIdAndStepIn(jobId, invalidated);

        // 6. Create fresh PENDING stages for the rerun path
        Map<StageKey, UUID> newIds = new LinkedHashMap<>();
        for (StageKey s : invalidated) {
            UUID newId = stageRepo.create(jobId, s, StageStatus.PENDING);
            newIds.put(s, newId);
        }

        // 7. Rewire [:TRIGGERS] edges — new stages connect to untouched siblings and each other
        stageRepo.rewireEdges(jobId, newIds, dag);

        // 8. Emit trigger for the rerun stage
        boolean triggered = stageRepo.tryTrigger(jobId, stage);
        if (triggered) {
            log.info("[ORCHESTRATION] Rerun triggered: jobId={} chapterId={} stage={}", jobId, chapterId, stage);
            eventPublisher.publishEvent(
                    new StageTriggeredEvent(this, jobId, chapterId, bookId, stage));
        }
    }

    /**
     * Delete all graph data tagged with a specific stageId.
     *
     * <p>Domain nodes and relationships created during a stage execution
     * carry a {@code stageId} property. This method removes all such
     * artifacts during cascade invalidation, ensuring the rerun starts
     * from clean state.
     *
     * <p>Two Cypher statements:
     * <ol>
     *   <li>Delete tagged nodes (DETACH DELETE also removes their relationships)</li>
     *   <li>Delete tagged relationships between non-tagged nodes</li>
     * </ol>
     */
    private void deleteDataByStageId(UUID stageId) {
        // Delete tagged nodes and their relationships
        neo4jClient.query("""
                MATCH (n {stageId: $stageId})
                DETACH DELETE n
                """)
                .bind(stageId.toString()).to("stageId")
                .run();

        // Delete tagged relationships between non-tagged nodes
        neo4jClient.query("""
                MATCH ()-[r {stageId: $stageId}]->()
                DELETE r
                """)
                .bind(stageId.toString()).to("stageId")
                .run();

        log.debug("[ORCHESTRATION] Deleted domain data for stageId={}", stageId);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private UUID findJobId(UUID chapterId) {
        return neo4jClient.query("""
                MATCH (j:ChapterIngestionJob {chapterId: $chapterId})
                RETURN j.id
                """)
                .bind(chapterId.toString()).to("chapterId")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) ->
                        UUID.fromString(record.get("j.id").asString()))
                .one()
                .orElse(null);
    }

    private UUID findChapterId(UUID jobId) {
        return neo4jClient.query("""
                MATCH (j:ChapterIngestionJob {id: $jobId})
                RETURN j.chapterId
                """)
                .bind(jobId.toString()).to("jobId")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) ->
                        UUID.fromString(record.get("j.chapterId").asString()))
                .one()
                .orElse(null);
    }

    private UUID findBookId(UUID chapterId) {
        if (chapterId == null) {
            return null;
        }
        return neo4jClient.query("""
                MATCH (c:Chapter {id: $chapterId})
                WHERE c.bookId IS NOT NULL
                RETURN c.bookId
                """)
                .bind(chapterId.toString()).to("chapterId")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) ->
                        UUID.fromString(record.get("c.bookId").asString()))
                .one()
                .orElse(null);
    }

    private UUID resolveBookId(UUID chapterId, UUID bookId, StageKey child) {
        if (bookId != null) {
            return bookId;
        }
        if (child.isBookLevel()) {
            return findBookId(chapterId);
        }
        return null;
    }

    StageDag dag() {
        return dag;
    }
}
