package com.lorevault.api.graph.object.consolidation.chapter;

import java.util.UUID;

public record ChapterObjectConsolidationResult(
        UUID chapterId,
        boolean success,
        int rawObjectsProcessed,
        int chapterObjectsCreated,
        String message
) {}
