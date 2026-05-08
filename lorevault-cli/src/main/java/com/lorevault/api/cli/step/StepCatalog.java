package com.lorevault.api.cli.step;

import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.scene.SceneDetectionOperation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Registry of available pipeline steps.
 *
 * <p>Slice 1 registers only {@link StepKey#SCENE_DETECTION}.
 * Additional steps will be wired in as their Operation interfaces are added to core.
 */
@Component
public class StepCatalog {

    private final Map<StepKey, StepDefinition> steps;

    public StepCatalog(SceneDetectionOperation sceneDetectionOperation) {
        this.steps = Map.of(
                StepKey.SCENE_DETECTION, new StepDefinition(
                        StepKey.SCENE_DETECTION,
                        "Detect semantic scene boundaries in chapter text",
                        Set.of(),
                        wrapOperation(sceneDetectionOperation::execute)
                )
        );
    }

    /**
     * Look up a step definition by key.
     *
     * @throws IllegalArgumentException if the step is not yet registered
     */
    public StepDefinition get(StepKey key) {
        StepDefinition def = steps.get(key);
        if (def == null) {
            throw new IllegalArgumentException("Step not yet implemented in CLI: " + key);
        }
        return def;
    }

    /** All registered step definitions. */
    public Map<StepKey, StepDefinition> all() {
        return steps;
    }

    /**
     * Wraps an {@code Operation::execute} method reference into the
     * {@code BiFunction<UUID, UUID, StepResult>} shape expected by {@link StepDefinition}.
     */
    private static BiFunction<UUID, UUID, StepResult> wrapOperation(BiFunction<UUID, UUID, StepResult> op) {
        return op;
    }
}