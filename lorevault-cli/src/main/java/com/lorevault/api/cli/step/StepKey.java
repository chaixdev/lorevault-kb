package com.lorevault.api.cli.step;

/**
 * Identifiers for the individual steps in the ingestion pipeline.
 *
 * <p>Each step corresponds to a handler that can be invoked independently
 * via the CLI. The enum order does <em>not</em> imply execution order —
 * that is defined by {@link StepDefinition#prerequisites()}.
 *
 * <p>Slice 1 delivers only {@link #SCENE_DETECTION}. Additional steps
 * will be added as the CLI module matures.
 */
public enum StepKey {

    SCENE_DETECTION,
    CHUNKING,
    EMBEDDING,
    COLLECTIVE_RESOLUTION,
    INDIVIDUAL_RESOLUTION,
    LOCATION_RESOLUTION,
    OBJECT_RESOLUTION,
    EVENT_RESOLUTION,
    EVENT_EMBEDDING,
    COLLECTIVE_REDUCTION,
    INDIVIDUAL_REDUCTION,
    LOCATION_REDUCTION,
    OBJECT_REDUCTION
}