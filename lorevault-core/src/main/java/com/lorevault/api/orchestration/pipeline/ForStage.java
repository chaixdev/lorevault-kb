package com.lorevault.api.orchestration.pipeline;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a {@link StageOperation} handler to the pipeline stage it services.
 *
 * <p>The {@code StageDispatcher} auto-discovers all {@code @ForStage}-annotated
 * beans and builds a {@code Map<StageKey, StageOperation>}. At startup, the
 * dispatcher asserts that every {@link StageKey} has exactly one handler
 * registered — duplicate or missing registrations are fail-fast errors.
 *
 * <pre>{@code
 * @ForStage(StageKey.SCENE_SEGMENTATION)
 * @Component
 * public class SceneDetectionHandler { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ForStage {
    StageKey value();
}
