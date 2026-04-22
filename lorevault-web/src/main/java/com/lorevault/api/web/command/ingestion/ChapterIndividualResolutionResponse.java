package com.lorevault.api.web.command.ingestion;

import java.util.UUID;

public class ChapterIndividualResolutionResponse {

    private UUID chapterId;
    private boolean processed;
    private int mentionCount;
    private int chapterIndividualCount;
    private String message;

    public ChapterIndividualResolutionResponse() {
    }

    public ChapterIndividualResolutionResponse(
            UUID chapterId,
            boolean processed,
            int mentionCount,
            int chapterIndividualCount,
            String message
    ) {
        this.chapterId = chapterId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterIndividualCount = chapterIndividualCount;
        this.message = message;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public void setChapterId(UUID chapterId) {
        this.chapterId = chapterId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public int getMentionCount() {
        return mentionCount;
    }

    public void setMentionCount(int mentionCount) {
        this.mentionCount = mentionCount;
    }

    public int getChapterIndividualCount() {
        return chapterIndividualCount;
    }

    public void setChapterIndividualCount(int chapterIndividualCount) {
        this.chapterIndividualCount = chapterIndividualCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
