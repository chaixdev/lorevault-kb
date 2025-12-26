package com.lorevault.api.event.ingestion;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all ingestion pipeline events.
 * Provides common job/chapter correlation and timing information.
 */
@Getter
public abstract class IngestionEvent extends ApplicationEvent {
    
    private final UUID jobId;
    private final UUID chapterId;
    private final Instant eventTime;
    
    protected IngestionEvent(Object source, UUID jobId, UUID chapterId) {
        super(source);
        this.jobId = jobId;
        this.chapterId = chapterId;
        this.eventTime = Instant.now();
    }
    
    /**
     * Returns a short description of this event type for logging.
     */
    public abstract String getEventType();
}
