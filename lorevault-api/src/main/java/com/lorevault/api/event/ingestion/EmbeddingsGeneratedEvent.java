package com.lorevault.api.event.ingestion;

import lombok.Getter;

import java.util.UUID;

/**
 * Emitted after embeddings have been generated for all chunks.
 * Triggers: Temporal edge creation and job completion.
 */
@Getter
public class EmbeddingsGeneratedEvent extends IngestionEvent {
    
    private final UUID bookId;
    private final int embeddedChunkCount;
    
    public EmbeddingsGeneratedEvent(Object source, UUID jobId, UUID chapterId, UUID bookId, int embeddedChunkCount) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.embeddedChunkCount = embeddedChunkCount;
    }
    
    @Override
    public String getEventType() {
        return "EMBEDDINGS_GENERATED";
    }
}
