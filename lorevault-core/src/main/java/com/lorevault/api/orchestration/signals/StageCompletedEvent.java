package com.lorevault.api.orchestration.signals;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StepResult;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Signals that a handler completed its work (success, skip, or failure).
 * The coordinator writes the result to the graph and evaluates DAG transitions.
 * Omits correlationId — jobId is sufficient for all correlation.
 */
public class StageCompletedEvent extends ApplicationEvent {

    private final UUID jobId;
    private final UUID chapterId;
    private final UUID bookId;       // nullable, for book-level stages
    private final StageKey stage;
    private final StepResult result;
    private final Instant eventTime;

    public StageCompletedEvent(Object source, UUID jobId, UUID chapterId, UUID bookId,
                               StageKey stage, StepResult result) {
        super(source);
        this.jobId = jobId;
        this.chapterId = chapterId;
        this.bookId = bookId;
        this.stage = stage;
        this.result = result;
        this.eventTime = Instant.now();
    }

    // Convenience for chapter-level stages
    public StageCompletedEvent(Object source, UUID jobId, UUID chapterId,
                               StageKey stage, StepResult result) {
        this(source, jobId, chapterId, null, stage, result);
    }

    public UUID getJobId() { return jobId; }
    public UUID getChapterId() { return chapterId; }
    public UUID getBookId() { return bookId; }
    public StageKey getStage() { return stage; }
    public StepResult getResult() { return result; }
    public Instant getEventTime() { return eventTime; }
}
