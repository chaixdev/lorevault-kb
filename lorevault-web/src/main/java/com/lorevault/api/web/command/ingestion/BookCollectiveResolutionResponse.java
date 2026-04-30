package com.lorevault.api.web.command.ingestion;

import java.util.UUID;

public class BookCollectiveResolutionResponse {

    private UUID bookId;
    private boolean processed;
    private int chapterCollectiveCount;
    private int bookCollectiveCount;
    private String message;

    public BookCollectiveResolutionResponse() {
    }

    public BookCollectiveResolutionResponse(
            UUID bookId,
            boolean processed,
            int chapterCollectiveCount,
            int bookCollectiveCount,
            String message
    ) {
        this.bookId = bookId;
        this.processed = processed;
        this.chapterCollectiveCount = chapterCollectiveCount;
        this.bookCollectiveCount = bookCollectiveCount;
        this.message = message;
    }

    public UUID getBookId() {
        return bookId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public int getChapterCollectiveCount() {
        return chapterCollectiveCount;
    }

    public int getBookCollectiveCount() {
        return bookCollectiveCount;
    }

    public String getMessage() {
        return message;
    }
}
