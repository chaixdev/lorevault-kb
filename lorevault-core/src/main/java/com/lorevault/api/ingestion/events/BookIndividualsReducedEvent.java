package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class BookIndividualsReducedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int chapterIndividualCount;
    private final int bookIndividualCount;

    public BookIndividualsReducedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterIndividualCount,
            int bookIndividualCount
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterIndividualCount = chapterIndividualCount;
        this.bookIndividualCount = bookIndividualCount;
    }

    public BookIndividualsReducedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int chapterIndividualCount,
            int bookIndividualCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.chapterIndividualCount = chapterIndividualCount;
        this.bookIndividualCount = bookIndividualCount;
    }

    @Override
    public String getEventType() {
        return "BOOK_INDIVIDUALS_REDUCED";
    }
}
