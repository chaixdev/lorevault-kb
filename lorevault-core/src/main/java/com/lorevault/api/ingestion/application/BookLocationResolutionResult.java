package com.lorevault.api.ingestion.application;

import java.util.UUID;

public record BookLocationResolutionResult(
    UUID bookId,
    boolean success,
    int chapterLocationsProcessed,
    int bookLocationsCreated,
    String message
) {}
