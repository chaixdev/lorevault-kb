package com.lorevault.api.orchestration.signals;

import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageResult;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Signals that a handler completed its work (success, skip, or failure).
 * The coordinator writes the result to the graph and evaluates DAG transitions.
 */
public class StageCompletedEvent extends ApplicationEvent {

    private final UUID jobId;
    private final UUID chapterId;
    private final UUID bookId;       // nullable, for book-level stages
    private final StageKey stage;
    private final StageResult result;
    private final String correlationId;
    private final Instant eventTime;

    public StageCompletedEvent(Object source, UUID jobId, UUID chapterId, UUID bookId,
                               StageKey stage, StageResult result, String correlationId) {
        super(source);
        this.jobId = jobId;
        this.chapterId = chapterId;
        this.bookId = bookId;
        this.stage = stage;
        this.result = result;
        this.correlationId = correlationId;
        this.eventTime = Instant.now();
    }

    // Convenience for chapter-level stages
    public StageCompletedEvent(Object source, UUID jobId, UUID chapterId,
                               StageKey stage, StageResult result, String correlationId) {
        this(source, jobId, chapterId, null, stage, result, correlationId);
    }

    public UUID getJobId() { return jobId; }
    public UUID getChapterId() { return chapterId; }
    public UUID getBookId() { return bookId; }
    public StageKey getStage() { return stage; }
    public StageResult getResult() { return result; }
    public String getCorrelationId() { return correlationId; }
    public Instant getEventTime() { return eventTime; }
}
