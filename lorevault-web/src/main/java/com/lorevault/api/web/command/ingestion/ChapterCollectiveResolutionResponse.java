package com.lorevault.api.web.command.ingestion;

import java.util.UUID;

public class ChapterCollectiveResolutionResponse {

    private UUID chapterId;
    private boolean processed;
    private int mentionCount;
    private int chapterCollectiveCount;
    private String message;

    public ChapterCollectiveResolutionResponse() {
    }

    public ChapterCollectiveResolutionResponse(
            UUID chapterId,
            boolean processed,
            int mentionCount,
            int chapterCollectiveCount,
            String message
    ) {
        this.chapterId = chapterId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterCollectiveCount = chapterCollectiveCount;
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

    public int getChapterCollectiveCount() {
        return chapterCollectiveCount;
    }

    public String getMessage() {
        return message;
    }
}
