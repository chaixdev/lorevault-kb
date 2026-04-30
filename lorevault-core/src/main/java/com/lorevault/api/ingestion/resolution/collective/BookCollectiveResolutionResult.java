package com.lorevault.api.ingestion.resolution.collective;

import java.util.UUID;

public record BookCollectiveResolutionResult(
        UUID bookId,
        boolean success,
        int chapterCollectivesProcessed,
        int bookCollectivesCreated,
        String message
) {}
