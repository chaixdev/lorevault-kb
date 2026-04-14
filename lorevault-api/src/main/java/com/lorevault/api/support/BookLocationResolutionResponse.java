package com.lorevault.api.support;

import java.util.UUID;

public class BookLocationResolutionResponse {

    private UUID bookId;
    private boolean processed;
    private int chapterLocationCount;
    private int bookLocationCount;
    private String message;

    public BookLocationResolutionResponse() {
    }

    public BookLocationResolutionResponse(UUID bookId, boolean processed, int chapterLocationCount, int bookLocationCount, String message) {
        this.bookId = bookId;
        this.processed = processed;
        this.chapterLocationCount = chapterLocationCount;
        this.bookLocationCount = bookLocationCount;
        this.message = message;
    }

    public UUID getBookId() {
        return bookId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public int getChapterLocationCount() {
        return chapterLocationCount;
    }

    public int getBookLocationCount() {
        return bookLocationCount;
    }

    public String getMessage() {
        return message;
    }
}
