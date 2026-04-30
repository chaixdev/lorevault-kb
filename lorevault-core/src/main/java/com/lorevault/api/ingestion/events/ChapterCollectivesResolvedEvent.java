package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ChapterCollectivesResolvedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int mentionCount;
    private final int chapterCollectiveCount;

    public ChapterCollectivesResolvedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterCollectiveCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterCollectiveCount = chapterCollectiveCount;
    }

    public ChapterCollectivesResolvedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterCollectiveCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterCollectiveCount = chapterCollectiveCount;
    }

    @Override
    public String getEventType() {
        return "CHAPTER_COLLECTIVES_RESOLVED";
    }
}
