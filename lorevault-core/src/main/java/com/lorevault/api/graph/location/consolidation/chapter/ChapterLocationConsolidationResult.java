package com.lorevault.api.graph.location.consolidation.chapter;

import java.util.UUID;

public record ChapterLocationConsolidationResult(
    UUID chapterId,
    boolean success,
    int rawLocationsProcessed,
    int chapterLocationsCreated,
    String message
) {}
