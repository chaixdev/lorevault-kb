package com.lorevault.api.graph.collective.consolidation.book;

import java.util.UUID;

public record BookCollectiveConsolidationResult(
        UUID bookId,
        boolean success,
        int chapterCollectivesProcessed,
        int bookCollectivesCreated,
        String message
) {}
