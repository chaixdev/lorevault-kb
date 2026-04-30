package com.lorevault.api.web.command.ingestion;

import java.util.UUID;

public class ChapterObjectResolutionResponse {

    private UUID chapterId;
    private boolean processed;
    private int mentionCount;
    private int chapterObjectCount;
    private String message;

    public ChapterObjectResolutionResponse() {
    }

    public ChapterObjectResolutionResponse(
            UUID chapterId,
            boolean processed,
            int mentionCount,
            int chapterObjectCount,
            String message
    ) {
        this.chapterId = chapterId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterObjectCount = chapterObjectCount;
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

    public int getChapterObjectCount() {
        return chapterObjectCount;
    }

    public String getMessage() {
        return message;
    }
}
