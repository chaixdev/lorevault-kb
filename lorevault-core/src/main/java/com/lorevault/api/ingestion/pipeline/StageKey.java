package com.lorevault.api.ingestion.pipeline;

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

    // ── Entity resolution lanes (chapter-level) ────────────────────────
    CHAPTER_INDIVIDUAL_RESOLUTION,
    CHAPTER_COLLECTIVE_RESOLUTION,
    CHAPTER_LOCATION_RESOLUTION,
    CHAPTER_OBJECT_RESOLUTION,
    CHAPTER_EVENT_RESOLUTION,

    // ── Entity reduction lanes (book-level) ────────────────────────────
    BOOK_INDIVIDUAL_REDUCTION,
    BOOK_COLLECTIVE_REDUCTION,
    BOOK_LOCATION_REDUCTION,
    BOOK_OBJECT_REDUCTION,

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
            CHAPTER_INDIVIDUAL_RESOLUTION,
            CHAPTER_COLLECTIVE_RESOLUTION,
            CHAPTER_LOCATION_RESOLUTION,
            CHAPTER_OBJECT_RESOLUTION,
            CHAPTER_EVENT_RESOLUTION,
            CHAPTER_EVENT_EMBEDDING
    );

    private static final Set<StageKey> BOOK_LEVEL_STAGES = Set.of(
            BOOK_INDIVIDUAL_REDUCTION,
            BOOK_COLLECTIVE_REDUCTION,
            BOOK_LOCATION_REDUCTION,
            BOOK_OBJECT_REDUCTION,
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
