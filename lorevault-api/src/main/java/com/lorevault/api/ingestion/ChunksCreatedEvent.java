package com.lorevault.api.ingestion;

import lombok.Getter;

import java.util.UUID;

/**
 * Emitted after text chunking has completed for all scenes.
 * Triggers: Embedding generation pipeline.
 */
@Getter
public class ChunksCreatedEvent extends IngestionEvent {
    
    private final UUID bookId;
    private final int chunkCount;
    
    public ChunksCreatedEvent(Object source, UUID jobId, UUID chapterId, UUID bookId, int chunkCount) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.chunkCount = chunkCount;
    }
    
    @Override
    public String getEventType() {
        return "CHUNKS_CREATED";
    }
}
