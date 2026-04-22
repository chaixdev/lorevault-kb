package com.lorevault.api.ingestion.application.result;

import java.util.UUID;

public record ChapterIndividualResolutionResult(
    UUID chapterId,
    boolean success,
    int rawIndividualsProcessed,
    int chapterIndividualsCreated,
    String message
) {}
