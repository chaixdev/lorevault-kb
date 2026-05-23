package com.lorevault.api.ingestion.events;

import com.lorevault.api.ingestion.pipeline.StageKey;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Signals that a pipeline stage is ready to execute.
 * Carries job context so handlers don't need to look anything up.
 * Omits correlationId — jobId is sufficient for all correlation.
 */
public class StageTriggeredEvent extends ApplicationEvent {

    private final UUID jobId;
    private final UUID chapterId;
    private final UUID bookId;       // nullable, for book-level stages
    private final StageKey stage;
    private final Instant eventTime;

    public StageTriggeredEvent(Object source, UUID jobId, UUID chapterId, UUID bookId, StageKey stage) {
        super(source);
        this.jobId = jobId;
        this.chapterId = chapterId;
        this.bookId = bookId;
        this.stage = stage;
        this.eventTime = Instant.now();
    }

    // Builder/convenience for chapter-level stages (bookId=null)
    public StageTriggeredEvent(Object source, UUID jobId, UUID chapterId, StageKey stage) {
        this(source, jobId, chapterId, null, stage);
    }

    public UUID getJobId() { return jobId; }
    public UUID getChapterId() { return chapterId; }
    public UUID getBookId() { return bookId; }
    public StageKey getStage() { return stage; }
    public Instant getEventTime() { return eventTime; }
}
