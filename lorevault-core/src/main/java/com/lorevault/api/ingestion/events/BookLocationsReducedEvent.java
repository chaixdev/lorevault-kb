package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class BookLocationsReducedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int chapterLocationCount;
    private final int bookLocationCount;

    public BookLocationsReducedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterLocationCount,
            int bookLocationCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterLocationCount = chapterLocationCount;
        this.bookLocationCount = bookLocationCount;
    }

    public BookLocationsReducedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterLocationCount,
            int bookLocationCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterLocationCount = chapterLocationCount;
        this.bookLocationCount = bookLocationCount;
    }

    @Override
    public String getEventType() {
        return "BOOK_LOCATIONS_REDUCED";
    }
}
