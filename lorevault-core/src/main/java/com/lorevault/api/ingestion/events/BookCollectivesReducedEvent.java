package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class BookCollectivesReducedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int chapterCollectiveCount;
    private final int bookCollectiveCount;

    public BookCollectivesReducedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterCollectiveCount,
            int bookCollectiveCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterCollectiveCount = chapterCollectiveCount;
        this.bookCollectiveCount = bookCollectiveCount;
    }

    public BookCollectivesReducedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterCollectiveCount,
            int bookCollectiveCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterCollectiveCount = chapterCollectiveCount;
        this.bookCollectiveCount = bookCollectiveCount;
    }

    @Override
    public String getEventType() {
        return "BOOK_COLLECTIVES_REDUCED";
    }
}
