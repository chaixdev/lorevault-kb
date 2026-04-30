package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class BookObjectsReducedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int chapterObjectCount;
    private final int bookObjectCount;

    public BookObjectsReducedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterObjectCount,
            int bookObjectCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterObjectCount = chapterObjectCount;
        this.bookObjectCount = bookObjectCount;
    }

    public BookObjectsReducedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterObjectCount,
            int bookObjectCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterObjectCount = chapterObjectCount;
        this.bookObjectCount = bookObjectCount;
    }

    @Override
    public String getEventType() {
        return "BOOK_OBJECTS_REDUCED";
    }
}
