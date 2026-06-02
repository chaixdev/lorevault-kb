package com.lorevault.api.graph.concept.consolidation.book;

import java.util.UUID;

public record BookConceptConsolidationResult(
        UUID bookId,
        boolean success,
        int chapterConceptsProcessed,
        int bookConceptsCreated,
        String message
) {}
