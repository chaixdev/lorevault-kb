package com.lorevault.api.ingestion.resolution.individual;

import java.util.UUID;

public record BookIndividualResolutionResult(
    UUID bookId,
    boolean success,
    int chapterIndividualsProcessed,
    int bookIndividualsCreated,
    String message
) {}
