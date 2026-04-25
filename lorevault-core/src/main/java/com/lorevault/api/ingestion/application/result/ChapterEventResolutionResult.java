package com.lorevault.api.ingestion.application.result;

import java.util.UUID;

public record ChapterEventResolutionResult(
    UUID chapterId,
    boolean success,
    int rawMentionsProcessed,
    int chapterEventsCreated,
    String message
) {}
