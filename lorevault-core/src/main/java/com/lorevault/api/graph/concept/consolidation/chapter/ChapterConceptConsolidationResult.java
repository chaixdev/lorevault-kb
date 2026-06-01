package com.lorevault.api.graph.concept.consolidation.chapter;

import java.util.UUID;

public record ChapterConceptConsolidationResult(
        UUID chapterId,
        boolean success,
        int rawConceptsProcessed,
        int chapterConceptsCreated,
        String message
) {}
