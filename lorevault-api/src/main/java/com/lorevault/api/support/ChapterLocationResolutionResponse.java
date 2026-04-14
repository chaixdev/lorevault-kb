package com.lorevault.api.support;

import java.util.UUID;

public class ChapterLocationResolutionResponse {

    private UUID chapterId;
    private boolean processed;
    private int mentionCount;
    private int chapterLocationCount;
    private String message;

    public ChapterLocationResolutionResponse() {
    }

    public ChapterLocationResolutionResponse(UUID chapterId, boolean processed, int mentionCount, int chapterLocationCount, String message) {
        this.chapterId = chapterId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterLocationCount = chapterLocationCount;
        this.message = message;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public int getMentionCount() {
        return mentionCount;
    }

    public int getChapterLocationCount() {
        return chapterLocationCount;
    }

    public String getMessage() {
        return message;
    }
}
