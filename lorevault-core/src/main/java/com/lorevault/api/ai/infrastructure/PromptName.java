package com.lorevault.api.ai.infrastructure;

/**
 * Type-safe prompt template identifiers.
 *
 * <p>Replaces raw string literals passed to {@code promptRepository.get("key")}
 * and {@code locationResolver.resolve("key")} with compile-time-checked constants.
 * Each enum value maps to the logical prompt name used in the prompt repository.
 */
public enum PromptName {

    CHAPTER_SEGMENTATION("chapter-segmentation"),
    SCENE_ANALYSIS("scene-analysis"),
    SCENE_ANALYSIS_USER("scene-analysis-user"),
    RAG_ANSWER_GENERATION("rag-answer-generation"),
    EVENT_COREF_SYSTEM("event-coref-system"),
    EVENT_COREF_USER("event-coref-user"),
    EVENT_MERGE_SYSTEM("event-merge-system"),
    EVENT_MERGE_USER("event-merge-user");

    private final String promptKey;

    PromptName(String promptKey) {
        this.promptKey = promptKey;
    }

    /** The logical prompt name used as a lookup key in the prompt repository. */
    public String promptKey() {
        return promptKey;
    }
}
