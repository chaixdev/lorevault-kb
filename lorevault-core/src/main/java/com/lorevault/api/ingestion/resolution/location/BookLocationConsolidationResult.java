package com.lorevault.api.ingestion.resolution.location;

import java.util.UUID;

public record BookLocationConsolidationResult(
    UUID bookId,
    boolean success,
    int chapterLocationsProcessed,
    int bookLocationsCreated,
    String message
) {}
