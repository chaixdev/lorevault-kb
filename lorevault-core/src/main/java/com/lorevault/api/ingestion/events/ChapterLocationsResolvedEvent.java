package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ChapterLocationsResolvedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int mentionCount;
    private final int chapterLocationCount;

    public ChapterLocationsResolvedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterLocationCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterLocationCount = chapterLocationCount;
    }

    public ChapterLocationsResolvedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterLocationCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterLocationCount = chapterLocationCount;
    }

    @Override
    public String getEventType() {
        return "CHAPTER_LOCATIONS_RESOLVED";
    }
}
