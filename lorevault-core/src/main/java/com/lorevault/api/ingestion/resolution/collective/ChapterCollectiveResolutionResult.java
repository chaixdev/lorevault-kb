package com.lorevault.api.ingestion.resolution.collective;

import java.util.UUID;

public record ChapterCollectiveResolutionResult(
        UUID chapterId,
        boolean success,
        int rawCollectivesProcessed,
        int chapterCollectivesCreated,
        String message
) {}
