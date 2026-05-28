package com.lorevault.api.ingestion.resolution.location;

import java.util.UUID;

public record ChapterLocationConsolidationResult(
    UUID chapterId,
    boolean success,
    int rawLocationsProcessed,
    int chapterLocationsCreated,
    String message
) {}
