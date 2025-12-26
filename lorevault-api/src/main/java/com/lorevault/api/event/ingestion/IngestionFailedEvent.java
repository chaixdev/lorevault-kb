package com.lorevault.api.event.ingestion;

import lombok.Getter;

import java.util.UUID;

/**
 * Emitted when any stage of the ingestion pipeline fails.
 * Contains the failed stage and error details for diagnosis and retry decisions.
 */
@Getter
public class IngestionFailedEvent extends IngestionEvent {
    
    private final String failedStage;
    private final String errorMessage;
    private final boolean retryable;
    
    public IngestionFailedEvent(Object source, UUID jobId, UUID chapterId, 
                                 String failedStage, String errorMessage, boolean retryable) {
        super(source, jobId, chapterId);
        this.failedStage = failedStage;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }
    
    @Override
    public String getEventType() {
        return "INGESTION_FAILED";
    }
}
