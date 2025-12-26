package com.lorevault.api.event.ingestion;

import lombok.Getter;

import java.util.UUID;

/**
 * Emitted after a chapter has been persisted to the database.
 * Triggers: Scene detection pipeline.
 */
@Getter
public class ChapterPersistedEvent extends IngestionEvent {
    
    private final UUID bookId;
    
    public ChapterPersistedEvent(Object source, UUID jobId, UUID chapterId, UUID bookId) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
    }
    
    @Override
    public String getEventType() {
        return "CHAPTER_PERSISTED";
    }
}
