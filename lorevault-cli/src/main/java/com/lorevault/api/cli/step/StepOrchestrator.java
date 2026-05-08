package com.lorevault.api.cli.step;

import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Executes individual pipeline steps on demand.
 *
 * <p>Slice 1 provides only {@code runStep(key, jobId, chapterId)}.
 * Future slices will add {@code runPipeline(bookId, throughStep, skipExisting)}.
 */
@Component
@Slf4j
public class StepOrchestrator {

    private final StepCatalog stepCatalog;

    public StepOrchestrator(StepCatalog stepCatalog) {
        this.stepCatalog = stepCatalog;
    }

    /**
     * Execute a single pipeline step.
     *
     * <p>The caller is responsible for ensuring prerequisites are met.
     * This method runs within a transaction provided by the caller or
     * by the underlying Operation implementation.
     *
     * @param key       which step to run
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return the result of the step execution
     */
    @Transactional
    public StepResult runStep(StepKey key, UUID jobId, UUID chapterId) {
        log.info("[CLI] Running step {} for job={} chapter={}", key, jobId, chapterId);
        StepDefinition def = stepCatalog.get(key);
        StepResult result = def.operation().apply(jobId, chapterId);
        if (result.success()) {
            log.info("[CLI] Step {} completed: {} ({}ms)", key, result.summary(), result.durationMs());
        } else {
            log.error("[CLI] Step {} failed: {}", key, result.summary());
        }
        return result;
    }
}