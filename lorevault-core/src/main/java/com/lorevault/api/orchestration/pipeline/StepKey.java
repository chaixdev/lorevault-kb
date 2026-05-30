package com.lorevault.api.orchestration.pipeline;

/**
 * Identifiers for all pipeline steps in the ingestion process.
 *
 * <p>Each key corresponds to a discrete, runnable step that an agent can
 * invoke via the step execution API. The key's {@link #getScope()} indicates
 * whether the step operates on a chapter or a book.
 *
 * <p>Step keys are used in {@link StepDefinition} for discoverability,
 * in {@link StepCatalog} for registration, and in the REST layer for
 * routing and event mapping.
 */
public enum StepKey {

    // ── Chapter-scoped steps ──────────────────────────────────────
    DETECT_SCENES("chapter"),
    CHUNK("chapter"),
    EMBED("chapter"),
    CHAPTER_CONSOLIDATE_INDIVIDUALS("chapter"),
    CHAPTER_CONSOLIDATE_COLLECTIVES("chapter"),
    CHAPTER_CONSOLIDATE_LOCATIONS("chapter"),
    CHAPTER_CONSOLIDATE_OBJECTS("chapter"),
    CHAPTER_CONSOLIDATE_EVENTS("chapter"),

    // ── Book-scoped steps ────────────────────────────────────────
    BOOK_CONSOLIDATE_INDIVIDUALS("book"),
    BOOK_CONSOLIDATE_COLLECTIVES("book"),
    BOOK_CONSOLIDATE_LOCATIONS("book"),
    BOOK_CONSOLIDATE_OBJECTS("book");

    private final String scope;

    StepKey(String scope) {
        this.scope = scope;
    }

    /** Whether this step operates on a chapter ({@code "chapter"}) or a book ({@code "book"}). */
    public String getScope() {
        return scope;
    }

    /**
     * Returns the kebab-case URL segment for this step key.
     *
     * <p>Examples: {@code DETECT_SCENES → "detect-scenes"},
     * {@code CHAPTER_CONSOLIDATE_INDIVIDUALS → "chapter-consolidate-individuals"},
     * {@code BOOK_CONSOLIDATE_INDIVIDUALS → "book-consolidate-individuals"}.
     */
    public String toUrlSegment() {
        return name().toLowerCase().replace('_', '-');
    }
}