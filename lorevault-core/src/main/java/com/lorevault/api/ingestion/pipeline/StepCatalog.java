package com.lorevault.api.ingestion.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry of all pipeline step definitions.
 *
 * <p>Provides metadata for the step query endpoint ({@code GET /api/query/ingestion/steps}).
 * Operation delegates are injected directly into command controllers rather than
 * through this catalog — the catalog is purely for discoverability.
 *
 * <p>Steps are listed in pipeline order: chapter-scoped steps first (in dependency
 * order), then book-scoped reduction steps.
 */
@Component
public class StepCatalog {

    private final List<StepDefinition> definitions = buildDefinitions();

    /**
     * Returns all registered step definitions in pipeline order.
     */
    public List<StepDefinition> all() {
        return definitions;
    }

    private static List<StepDefinition> buildDefinitions() {
        return List.of(
                // ── Chapter-scoped steps ──────────────────────────────────
                new StepDefinition(
                        StepKey.DETECT_SCENES,
                        "Detect semantic scene boundaries in chapter text",
                        "chapter",
                        List.of()
                ),
                new StepDefinition(
                        StepKey.CHUNK,
                        "Split detected scenes into text chunks for embedding",
                        "chapter",
                        List.of(StepKey.DETECT_SCENES)
                ),
                new StepDefinition(
                        StepKey.EMBED,
                        "Generate vector embeddings for scene chunks",
                        "chapter",
                        List.of(StepKey.CHUNK)
                ),
                new StepDefinition(
                        StepKey.CHAPTER_CONSOLIDATE_INDIVIDUALS,
                        "Consolidate individual entity mentions across scenes",
                        "chapter",
                        List.of(StepKey.DETECT_SCENES)
                ),
                new StepDefinition(
                        StepKey.CHAPTER_CONSOLIDATE_COLLECTIVES,
                        "Consolidate collective entity mentions across scenes",
                        "chapter",
                        List.of(StepKey.DETECT_SCENES)
                ),
                new StepDefinition(
                        StepKey.CHAPTER_CONSOLIDATE_LOCATIONS,
                        "Consolidate location entity mentions across scenes",
                        "chapter",
                        List.of(StepKey.DETECT_SCENES)
                ),
                new StepDefinition(
                        StepKey.CHAPTER_CONSOLIDATE_OBJECTS,
                        "Consolidate object entity mentions across scenes",
                        "chapter",
                        List.of(StepKey.DETECT_SCENES)
                ),
                new StepDefinition(
                        StepKey.CHAPTER_CONSOLIDATE_EVENTS,
                        "Consolidate narrative events across scenes",
                        "chapter",
                        List.of(StepKey.DETECT_SCENES)
                ),

                // ── Book-scoped steps ────────────────────────────────────
                new StepDefinition(
                        StepKey.BOOK_CONSOLIDATE_INDIVIDUALS,
                        "Consolidate chapter-level individuals to book-level entities",
                        "book",
                        List.of(StepKey.CHAPTER_CONSOLIDATE_INDIVIDUALS)
                ),
                new StepDefinition(
                        StepKey.BOOK_CONSOLIDATE_COLLECTIVES,
                        "Consolidate chapter-level collectives to book-level entities",
                        "book",
                        List.of(StepKey.CHAPTER_CONSOLIDATE_COLLECTIVES)
                ),
                new StepDefinition(
                        StepKey.BOOK_CONSOLIDATE_LOCATIONS,
                        "Consolidate chapter-level locations to book-level entities",
                        "book",
                        List.of(StepKey.CHAPTER_CONSOLIDATE_LOCATIONS)
                ),
                new StepDefinition(
                        StepKey.BOOK_CONSOLIDATE_OBJECTS,
                        "Consolidate chapter-level objects to book-level entities",
                        "book",
                        List.of(StepKey.CHAPTER_CONSOLIDATE_OBJECTS)
                )
        );
    }
}