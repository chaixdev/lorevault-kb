package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.timeline.infrastructure.CrossChapterBoundaryProjection;

import java.util.List;

public record DefaultTemporalEdgeCreationResult(
        int inChapterEdgesCreated,
        int crossChapterEdgesCreated,
        List<CrossChapterBoundaryProjection> newlyCreatedCrossChapterBoundaries
) {
}
