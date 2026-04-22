package com.lorevault.api.ingestion.application.result;

import java.util.UUID;

public record BookLocationResolutionResult(
    UUID bookId,
    boolean success,
    int chapterLocationsProcessed,
    int bookLocationsCreated,
    String message
) {}
