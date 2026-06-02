package com.lorevault.api.orchestration.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@ForStage(StageKey.INGESTION_COMPLETE)
public class IngestionCompleteHandler implements StageOperation {

    @Override
    public StageResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        log.info("[ORCHESTRATION] Ingestion complete: jobId={}", jobId);
        return StageResult.success(StageKey.INGESTION_COMPLETE, "Ingestion pipeline completed", 0L);
    }
}
