package com.lorevault.api.ingestion.orchestration;

import com.lorevault.api.ingestion.pipeline.StageKey;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Graph operations for {@code StageOutput} nodes — immutable proof-of-work audit trail.
 *
 * <p>Provides idempotency checks (does output already exist for this scope+step?)
 * and bulk deletion during cascade invalidation.
 */
@Repository
public class StageOutputGraphRepository {

    private final Neo4jClient neo4jClient;

    public StageOutputGraphRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // ── Save ────────────────────────────────────────────────────────

    public void save(StageOutput output) {
        if (output.getChapterId() != null) {
            neo4jClient.query("""
                    CREATE (o:StageOutput {id: $id, chapterId: $chapterId, step: $step, completedAt: $completedAt})
                    """)
                    .bind(output.getId().toString()).to("id")
                    .bind(output.getChapterId().toString()).to("chapterId")
                    .bind(output.getStep().name()).to("step")
                    .bind(output.getCompletedAt()).to("completedAt")
                    .run();
        } else {
            neo4jClient.query("""
                    CREATE (o:StageOutput {id: $id, bookId: $bookId, step: $step, completedAt: $completedAt})
                    """)
                    .bind(output.getId().toString()).to("id")
                    .bind(output.getBookId() != null ? output.getBookId().toString() : null).to("bookId")
                    .bind(output.getStep().name()).to("step")
                    .bind(output.getCompletedAt()).to("completedAt")
                    .run();
        }
    }

    // ── Idempotency checks ──────────────────────────────────────────

    /**
     * Returns true if a StageOutput already exists for this chapter+step.
     * Used by chapter-level handlers to skip already-completed work.
     */
    public boolean existsByChapterIdAndStep(UUID chapterId, StageKey step) {
        return neo4jClient.query("""
                MATCH (o:StageOutput {chapterId: $chapterId, step: $step})
                RETURN count(o) > 0 AS exists
                """)
                .bind(chapterId.toString()).to("chapterId")
                .bind(step.name()).to("step")
                .fetchAs(Boolean.class)
                .mappedBy((typeSystem, record) -> record.get("exists").asBoolean())
                .one()
                .orElse(false);
    }

    /**
     * Returns true if a StageOutput already exists for this book+step.
     * Used by book-level handlers to skip already-completed work.
     */
    public boolean existsByBookIdAndStep(UUID bookId, StageKey step) {
        return neo4jClient.query("""
                MATCH (o:StageOutput {bookId: $bookId, step: $step})
                RETURN count(o) > 0 AS exists
                """)
                .bind(bookId != null ? bookId.toString() : null).to("bookId")
                .bind(step.name()).to("step")
                .fetchAs(Boolean.class)
                .mappedBy((typeSystem, record) -> record.get("exists").asBoolean())
                .one()
                .orElse(false);
    }

    /**
     * Returns the most recent StageOutput for a chapter+step (for audit/debug).
     */
    public java.util.Optional<StageOutput> findLatestByChapterIdAndStep(UUID chapterId, StageKey step) {
        return neo4jClient.query("""
                MATCH (o:StageOutput {chapterId: $chapterId, step: $step})
                RETURN o
                ORDER BY o.completedAt DESC
                LIMIT 1
                """)
                .bind(chapterId.toString()).to("chapterId")
                .bind(step.name()).to("step")
                .fetchAs(StageOutput.class)
                .mappedBy((typeSystem, record) -> {
                    var node = record.get("o").asNode();
                    return StageOutput.builder()
                            .id(UUID.fromString(node.get("id").asString()))
                            .chapterId(node.containsKey("chapterId") && !node.get("chapterId").isNull()
                                    ? UUID.fromString(node.get("chapterId").asString()) : null)
                            .bookId(node.containsKey("bookId") && !node.get("bookId").isNull()
                                    ? UUID.fromString(node.get("bookId").asString()) : null)
                            .step(StageKey.valueOf(node.get("step").asString()))
                            .completedAt(safeLocalDateTime(node, "completedAt"))
                            .build();
                })
                .one();
    }

    // ── Book-level stage detection ─────────────────────────────────

    private static final Set<StageKey> BOOK_LEVEL_STAGES = Set.of(
            StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
            StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
            StageKey.BOOK_LOCATION_CONSOLIDATION,
            StageKey.BOOK_OBJECT_CONSOLIDATION
    );

    private static boolean isBookLevelStage(StageKey stage) {
        return BOOK_LEVEL_STAGES.contains(stage);
    }

    // ── Cascade invalidation ────────────────────────────────────────

    /**
     * Delete all StageOutput nodes for a job's chapter/scope and step set.
     * Deletes both chapter-level ({@code chapterId}) and book-level
     * ({@code bookId}) StageOutputs. Used during cascade invalidation to
     * prevent false SKIP on rerun.
     *
     * @param jobId  the ingestion job
     * @param bookId the book scope for book-level StageOutputs (nullable;
     *               derived from the job's chapter if null and steps contain
     *               any book-level stage)
     * @param steps  the steps whose StageOutputs should be deleted
     */
    public void deleteByJobAndSteps(UUID jobId, UUID bookId, Set<StageKey> steps) {
        List<String> stepNames = steps.stream().map(StageKey::name).toList();

        UUID chapterId = neo4jClient.query("""
                MATCH (j:ChapterIngestionJob {id: $jobId})
                RETURN j.chapterId
                """)
                .bind(jobId.toString()).to("jobId")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) -> UUID.fromString(record.get("j.chapterId").asString()))
                .one()
                .orElse(null);

        // 1. Delete chapter-level StageOutputs
        if (chapterId != null) {
            neo4jClient.query("""
                    MATCH (o:StageOutput {chapterId: $chapterId})
                    WHERE o.step IN $steps
                    DETACH DELETE o
                    """)
                    .bind(chapterId.toString()).to("chapterId")
                    .bind(stepNames).to("steps")
                    .run();
        }

        // 2. Delete book-level StageOutputs if any book-level steps are present
        boolean hasBookLevelSteps = steps.stream().anyMatch(StageOutputGraphRepository::isBookLevelStage);
        if (hasBookLevelSteps) {
            UUID resolvedBookId = bookId;
            if (resolvedBookId == null && chapterId != null) {
                resolvedBookId = deriveBookIdFromChapter(chapterId);
            }
            if (resolvedBookId != null) {
                List<String> bookStepNames = steps.stream()
                        .filter(StageOutputGraphRepository::isBookLevelStage)
                        .map(StageKey::name)
                        .toList();
                neo4jClient.query("""
                        MATCH (o:StageOutput {bookId: $bookId})
                        WHERE o.step IN $steps
                        DETACH DELETE o
                        """)
                        .bind(resolvedBookId.toString()).to("bookId")
                        .bind(bookStepNames).to("steps")
                        .run();
            }
        }
    }

    /**
     * Traverse from a Chapter node to its parent Book and return the Book's id.
     */
    private UUID deriveBookIdFromChapter(UUID chapterId) {
        return neo4jClient.query("""
                MATCH (c:Chapter {id: $chapterId})-[:IN_BOOK]->(b:Book)
                RETURN b.id
                """)
                .bind(chapterId.toString()).to("chapterId")
                .fetchAs(UUID.class)
                .mappedBy((typeSystem, record) -> UUID.fromString(record.get("b.id").asString()))
                .one()
                .orElse(null);
    }

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
