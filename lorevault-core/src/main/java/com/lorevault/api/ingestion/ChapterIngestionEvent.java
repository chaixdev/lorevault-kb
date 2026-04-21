package com.lorevault.api.ingestion;

import java.util.UUID;

public class ChapterIngestionEvent extends IngestionEvent {

    public ChapterIngestionEvent(Object source, UUID jobId, UUID chapterId) {
        super(source, jobId, chapterId);
    }

    @Override
    public String getEventType() {
        return "CHAPTER_INGESTION_STARTED";
    }
}
