package com.lorevault.api.orchestration.pipeline;

import java.util.List;

/**
 * Metadata about a single pipeline step.
 *
 * <p>Used by the step query endpoint to describe available steps,
 * their scope, and prerequisite relationships.
 *
 * @param key            unique step identifier
 * @param description    human-readable description of what the step does
 * @param scope          {@code "chapter"} or {@code "book"} — the entity type the step operates on
 * @param prerequisites steps that must complete before this step can run
 */
public record StepDefinition(
        StepKey key,
        String description,
        String scope,
        List<StepKey> prerequisites
) {
    public StepDefinition {
        prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
    }
}