package com.lorevault.api.event.ingestion;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Emitted after AI scene detection has completed and scenes are persisted.
 * Triggers: Chunking pipeline.
 */
@Getter
public class ScenesDetectedEvent extends IngestionEvent {
    
    private final UUID bookId;
    private final List<UUID> sceneIds;
    private final int sceneCount;
    
    public ScenesDetectedEvent(Object source, UUID jobId, UUID chapterId, UUID bookId, List<UUID> sceneIds) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.sceneIds = sceneIds;
        this.sceneCount = sceneIds.size();
    }
    
    @Override
    public String getEventType() {
        return "SCENES_DETECTED";
    }
}
