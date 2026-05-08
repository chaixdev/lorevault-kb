package com.lorevault.api.cli.step;

import com.lorevault.api.ingestion.pipeline.StepResult;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Metadata for a single pipeline step: its key, human-readable description,
 * prerequisite steps, and the operation that executes it.
 *
 * @param key            unique step identifier
 * @param description    one-line human-readable description
 * @param prerequisites   steps that must complete before this one can run
 * @param operation      the function that executes the step, given (jobId, chapterId)
 */
public record StepDefinition(
        StepKey key,
        String description,
        Set<StepKey> prerequisites,
        BiFunction<UUID, UUID, StepResult> operation
) {}