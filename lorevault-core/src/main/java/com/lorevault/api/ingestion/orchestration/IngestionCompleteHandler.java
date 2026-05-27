package com.lorevault.api.ingestion.orchestration;

import com.lorevault.api.ingestion.events.StageCompletedEvent;
import com.lorevault.api.ingestion.events.StageTriggeredEvent;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class IngestionCompleteHandler {

    private final ApplicationEventPublisher eventPublisher;
    private final StageGraphRepository stageRepo;

    public IngestionCompleteHandler(
            ApplicationEventPublisher eventPublisher,
            StageGraphRepository stageRepo
    ) {
        this.eventPublisher = eventPublisher;
        this.stageRepo = stageRepo;
    }

    @Async("ingestionLaneTaskExecutor")
    @EventListener
    public void onTrigger(StageTriggeredEvent event) {
        // Stage key guard: reject events for other stages
        if (event.getStage() != StageKey.INGESTION_COMPLETE) return;

        UUID jobId = event.getJobId();
        UUID chapterId = event.getChapterId();
        UUID bookId = event.getBookId();

        if (!stageRepo.setRunningConditionally(jobId, event.getStage())) {
            return;
        }

        log.info("[ORCHESTRATION] Ingestion complete: jobId={} chapterId={} bookId={}", jobId, chapterId, bookId);

        eventPublisher.publishEvent(new StageCompletedEvent(
                this, jobId, chapterId, bookId, event.getStage(),
                StepResult.success(StageKey.INGESTION_COMPLETE, "Ingestion complete", 0L)));
    }
}
