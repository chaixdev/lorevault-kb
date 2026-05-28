package com.lorevault.api.ingestion.resolution.object;

import java.util.UUID;

public record BookObjectConsolidationResult(
        UUID bookId,
        boolean success,
        int chapterObjectsProcessed,
        int bookObjectsCreated,
        String message
) {}
