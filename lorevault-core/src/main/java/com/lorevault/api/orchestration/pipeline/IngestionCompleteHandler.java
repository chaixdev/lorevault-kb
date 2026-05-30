package com.lorevault.api.orchestration.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.INGESTION_COMPLETE)
public class IngestionCompleteHandler implements StageOperation {

    @Override
    public StepResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        log.info("[ORCHESTRATION] Ingestion complete: jobId={}", jobId);
        return StepResult.success(StageKey.INGESTION_COMPLETE, "Ingestion pipeline completed", 0L);
    }
}
