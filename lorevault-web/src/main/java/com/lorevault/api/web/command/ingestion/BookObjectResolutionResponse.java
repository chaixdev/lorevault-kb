package com.lorevault.api.web.command.ingestion;

import java.util.UUID;

public class BookObjectResolutionResponse {

    private UUID bookId;
    private boolean processed;
    private int chapterObjectCount;
    private int bookObjectCount;
    private String message;

    public BookObjectResolutionResponse() {
    }

    public BookObjectResolutionResponse(
            UUID bookId,
            boolean processed,
            int chapterObjectCount,
            int bookObjectCount,
            String message
    ) {
        this.bookId = bookId;
        this.processed = processed;
        this.chapterObjectCount = chapterObjectCount;
        this.bookObjectCount = bookObjectCount;
        this.message = message;
    }

    public UUID getBookId() {
        return bookId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public int getChapterObjectCount() {
        return chapterObjectCount;
    }

    public int getBookObjectCount() {
        return bookObjectCount;
    }

    public String getMessage() {
        return message;
    }
}
