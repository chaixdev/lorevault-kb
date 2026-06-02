package com.lorevault.api.graph.object.consolidation.book;

import java.util.UUID;

public record BookObjectConsolidationResult(
        UUID bookId,
        boolean success,
        int chapterObjectsProcessed,
        int bookObjectsCreated,
        String message
) {}
