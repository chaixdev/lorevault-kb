package com.lorevault.api.graph.location.consolidation.book;

import java.util.UUID;

public record BookLocationConsolidationResult(
    UUID bookId,
    boolean success,
    int chapterLocationsProcessed,
    int bookLocationsCreated,
    String message
) {}
