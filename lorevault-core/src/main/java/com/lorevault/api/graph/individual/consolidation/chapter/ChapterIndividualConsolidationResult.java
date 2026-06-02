package com.lorevault.api.graph.individual.consolidation.chapter;

import java.util.UUID;

public record ChapterIndividualConsolidationResult(
    UUID chapterId,
    boolean success,
    int rawIndividualsProcessed,
    int chapterIndividualsCreated,
    String message
) {}
