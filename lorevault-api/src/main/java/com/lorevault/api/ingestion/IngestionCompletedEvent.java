package com.lorevault.api.ingestion;

import lombok.Getter;

import java.util.UUID;

/**
 * Emitted when the entire ingestion pipeline has completed successfully.
 * This is a terminal event - no further processing is triggered.
 */
@Getter
public class IngestionCompletedEvent extends IngestionEvent {
    
    private final int totalScenes;
    private final int totalChunks;
    private final int totalEmbeddings;
    
    public IngestionCompletedEvent(Object source, UUID jobId, UUID chapterId, 
                                    int totalScenes, int totalChunks, int totalEmbeddings) {
        super(source, jobId, chapterId);
        this.totalScenes = totalScenes;
        this.totalChunks = totalChunks;
        this.totalEmbeddings = totalEmbeddings;
    }
    
    @Override
    public String getEventType() {
        return "INGESTION_COMPLETED";
    }
}
