package com.lorevault.api.ingestion.resolution.event;

import java.util.UUID;

public record ChapterEventResolutionResult(
    UUID chapterId,
    boolean success,
    int rawMentionsProcessed,
    int chapterEventsCreated,
    int failedCorefWindowCount,
    String message
) {}
