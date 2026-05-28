package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ChapterEventsConsolidatedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int mentionCount;
    private final int chapterEventCount;
    private final int failedCorefWindowCount;

    public ChapterEventsConsolidatedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterEventCount,
            int failedCorefWindowCount
    ) {
        this(source, jobId, jobId, chapterId, bookId, processed, mentionCount, chapterEventCount, failedCorefWindowCount);
    }

    public ChapterEventsConsolidatedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterEventCount,
            int failedCorefWindowCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterEventCount = chapterEventCount;
        this.failedCorefWindowCount = failedCorefWindowCount;
    }

    @Override
    public String getEventType() {
        return "CHAPTER_EVENTS_CONSOLIDATED";
    }
}
