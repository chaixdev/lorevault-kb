package com.lorevault.api.support;

import java.util.UUID;

public class BookIndividualResolutionResponse {

    private UUID bookId;
    private boolean processed;
    private int chapterIndividualCount;
    private int bookIndividualCount;
    private String message;

    public BookIndividualResolutionResponse() {
    }

    public BookIndividualResolutionResponse(
            UUID bookId,
            boolean processed,
            int chapterIndividualCount,
            int bookIndividualCount,
            String message
    ) {
        this.bookId = bookId;
        this.processed = processed;
        this.chapterIndividualCount = chapterIndividualCount;
        this.bookIndividualCount = bookIndividualCount;
        this.message = message;
    }

    public UUID getBookId() {
        return bookId;
    }

    public void setBookId(UUID bookId) {
        this.bookId = bookId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public int getChapterIndividualCount() {
        return chapterIndividualCount;
    }

    public void setChapterIndividualCount(int chapterIndividualCount) {
        this.chapterIndividualCount = chapterIndividualCount;
    }

    public int getBookIndividualCount() {
        return bookIndividualCount;
    }

    public void setBookIndividualCount(int bookIndividualCount) {
        this.bookIndividualCount = bookIndividualCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
