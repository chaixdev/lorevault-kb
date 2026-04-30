package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ChapterObjectsResolvedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int mentionCount;
    private final int chapterObjectCount;

    public ChapterObjectsResolvedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterObjectCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterObjectCount = chapterObjectCount;
    }

    public ChapterObjectsResolvedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterObjectCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterObjectCount = chapterObjectCount;
    }

    @Override
    public String getEventType() {
        return "CHAPTER_OBJECTS_RESOLVED";
    }
}
