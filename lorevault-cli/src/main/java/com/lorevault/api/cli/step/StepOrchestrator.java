package com.lorevault.api.cli.step;

import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
     * Transaction boundaries are managed by the underlying Operation
     * implementations — each handler method declares its own {@code @Transactional}
     * scope for DB writes, keeping LLM calls outside transaction boundaries.
     *
     * @param key       which step to run
     * @param jobId     the ingestion job ID (created by {@code prepare})
     * @param chapterId the chapter to process
     * @return the result of the step execution
     */
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