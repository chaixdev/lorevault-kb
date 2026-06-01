package com.lorevault.api.orchestration.pipeline;

import java.util.Set;

/**
 * Identifies a vertex in the ingestion pipeline DAG.
 *
 * <p>Each constant maps to exactly one {@code Stage} node in the graph per
 * {@code ChapterIngestionJob}. The DAG topology is defined in {@code StageDag},
 * not distributed across handler annotations.
 *
 * <p>StageKey is the canonical identifier for pipeline stages. It is used by
 * {@code StageDag} for topology, by {@code Stage} nodes for routing, and by
 * {@code StageTriggeredEvent}/{@code StageCompletedEvent} for dispatch.
 */
public enum StageKey {

    // ── Root (no incoming [:TRIGGERS] edges) ──────────────────────────
    SCENE_SEGMENTATION,

    // ── Content lane ──────────────────────────────────────────────────
    CHUNKING,
    EMBEDDING,

    // ── Entity consolidation lanes (chapter-level) ─────────────────────
    CHAPTER_INDIVIDUAL_CONSOLIDATION,
    CHAPTER_COLLECTIVE_CONSOLIDATION,
    CHAPTER_LOCATION_CONSOLIDATION,
    CHAPTER_OBJECT_CONSOLIDATION,
    CHAPTER_EVENT_CONSOLIDATION,
    CHAPTER_CONCEPT_CONSOLIDATION,

    // ── Entity consolidation lanes (book-level) ────────────────────────
    BOOK_INDIVIDUAL_CONSOLIDATION,
    BOOK_COLLECTIVE_CONSOLIDATION,
    BOOK_LOCATION_CONSOLIDATION,
    BOOK_OBJECT_CONSOLIDATION,
    BOOK_CONCEPT_CONSOLIDATION,

    // ── Event lane (chapter → book) ────────────────────────────────────
    CHAPTER_EVENT_EMBEDDING,
    BOOK_EVENT_CANDIDATE_GENERATION,

    // ── Terminal barrier ───────────────────────────────────────────────
    INGESTION_COMPLETE;

    // ── Classification sets ────────────────────────────────────────────

    private static final Set<StageKey> CHAPTER_STAGES = Set.of(
            SCENE_SEGMENTATION,
            CHUNKING,
            EMBEDDING,
            CHAPTER_INDIVIDUAL_CONSOLIDATION,
            CHAPTER_COLLECTIVE_CONSOLIDATION,
            CHAPTER_LOCATION_CONSOLIDATION,
            CHAPTER_OBJECT_CONSOLIDATION,
            CHAPTER_EVENT_CONSOLIDATION,
            CHAPTER_CONCEPT_CONSOLIDATION,
            CHAPTER_EVENT_EMBEDDING
    );

    private static final Set<StageKey> BOOK_LEVEL_STAGES = Set.of(
            BOOK_INDIVIDUAL_CONSOLIDATION,
            BOOK_COLLECTIVE_CONSOLIDATION,
            BOOK_LOCATION_CONSOLIDATION,
            BOOK_OBJECT_CONSOLIDATION,
            BOOK_CONCEPT_CONSOLIDATION,
            BOOK_EVENT_CANDIDATE_GENERATION
    );

    /**
     * Returns true if this stage operates at chapter scope (idempotency keyed by chapterId).
     */
    public boolean isChapterStage() {
        return CHAPTER_STAGES.contains(this);
    }

    /**
     * Returns true if this stage operates at book scope (idempotency keyed by bookId).
     */
    public boolean isBookLevel() {
        return BOOK_LEVEL_STAGES.contains(this);
    }
}
