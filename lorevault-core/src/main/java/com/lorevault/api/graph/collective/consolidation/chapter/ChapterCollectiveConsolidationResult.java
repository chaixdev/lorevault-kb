package com.lorevault.api.graph.collective.consolidation.chapter;

import java.util.UUID;

public record ChapterCollectiveConsolidationResult(
        UUID chapterId,
        boolean success,
        int rawCollectivesProcessed,
        int chapterCollectivesCreated,
        String message
) {}
