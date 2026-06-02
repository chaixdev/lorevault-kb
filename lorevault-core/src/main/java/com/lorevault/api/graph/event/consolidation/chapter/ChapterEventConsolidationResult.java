package com.lorevault.api.graph.event.consolidation.chapter;

import java.util.UUID;

public record ChapterEventConsolidationResult(
    UUID chapterId,
    boolean success,
    int rawMentionsProcessed,
    int chapterEventsCreated,
    int failedCorefWindowCount,
    String message
) {}
