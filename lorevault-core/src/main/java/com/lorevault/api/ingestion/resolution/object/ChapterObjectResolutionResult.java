package com.lorevault.api.ingestion.resolution.object;

import java.util.UUID;

public record ChapterObjectResolutionResult(
        UUID chapterId,
        boolean success,
        int rawObjectsProcessed,
        int chapterObjectsCreated,
        String message
) {}
