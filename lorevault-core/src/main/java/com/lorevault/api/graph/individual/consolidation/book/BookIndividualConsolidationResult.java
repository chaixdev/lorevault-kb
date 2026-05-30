package com.lorevault.api.graph.individual.consolidation.book;

import java.util.UUID;

public record BookIndividualConsolidationResult(
    UUID bookId,
    boolean success,
    int chapterIndividualsProcessed,
    int bookIndividualsCreated,
    String message
) {}
