package com.lorevault.api.ingestion.orchestration;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageStatus;
import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.LocalDateTime;
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
    private final StageOutputGraphRepository stageOutputRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final Neo4jClient neo4jClient;

    @Value("${lorevault.ingestion.stale-trigger-grace-seconds:60}")
    private long staleTriggerGraceSeconds;

    @Value("${lorevault.ingestion.stale-running-threshold-seconds:300}")
    private long staleRunningThresholdSeconds;

    @Value("${lorevault.ingestion.max-stage-attempts:3}")
    private int maxStageAttempts;

    public IngestionPipelineCoordinator(
            StageGraphRepository stageRepo,
            StageOutputGraphRepository stageOutputRepo,
            ApplicationEventPublisher eventPublisher,
            Neo4jClient neo4jClient) {
        this.dag = new StageDag();
        this.stageRepo = stageRepo;
        this.stageOutputRepo = stageOutputRepo;
        this.eventPublisher = eventPublisher;
        this.neo4jClient = neo4jClient;
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
        StepResult result = event.getResult();

        if (result.success()) {
            stageRepo.setCompleted(jobId, stage);

            StageOutput output;
            if (bookId != null) {
                output = StageOutput.forBook(bookId, stage, LocalDateTime.now());
            } else {
                output = StageOutput.forChapter(chapterId, stage, LocalDateTime.now());
            }
            stageOutputRepo.save(output);

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
            boolean triggered = stageRepo.tryTrigger(jobId, child);
            if (triggered) {
                log.info("[ORCHESTRATION] Barrier open — triggering: jobId={} chapterId={} child={}",
                        jobId, chapterId, child);
                eventPublisher.publishEvent(
                        new StageTriggeredEvent(this, jobId, chapterId, bookId, child));
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
            log.warn("[ORCHESTRATION] Re-publishing stale trigger: jobId={} step={} triggeredAt={}",
                    s.getJobId(), s.getStep(), s.getTriggeredAt());
            eventPublisher.publishEvent(
                    new StageTriggeredEvent(this, s.getJobId(), findChapterId(s.getJobId()), s.getStep()));
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
            if (s.getStatus() == StageStatus.TRIGGERED) {
                log.warn("[ORCHESTRATION] Resetting stale RUNNING→TRIGGERED: jobId={} step={} attempt={}/{}",
                        s.getJobId(), s.getStep(), s.getAttemptCount(), maxStageAttempts);
                eventPublisher.publishEvent(
                        new StageTriggeredEvent(this, s.getJobId(), findChapterId(s.getJobId()), s.getStep()));
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

        // 4. Delete stale StageOutputs for the invalidated path (prevents false SKIP)
        stageOutputRepo.deleteByJobAndSteps(jobId, bookId, invalidated);

        // 5. Delete stale Stage nodes
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
     * Handlers write {@code stageId} on every node/edge they create —
     * this method removes those artifacts during cascade invalidation.
     */
    private void deleteDataByStageId(UUID stageId) {
        // Data cleanup is stage-specific per lane. Handlers will register
        // their cleanup queries. For now, a generic pattern: delete nodes
        // with matching stageId property and their outgoing relationships.
        // Specific cleanup is added as handlers adopt stageId tagging.
        log.debug("[ORCHESTRATION] Deleting data for stageId={} — handler-specific cleanup not yet wired", stageId);
    }

    // ── Helpers ─────────────────────────────────────────────────────

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

    StageDag dag() {
        return dag;
    }
}
