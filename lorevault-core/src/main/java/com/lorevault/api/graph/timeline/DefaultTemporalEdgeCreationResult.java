package com.lorevault.api.graph.timeline;

import com.lorevault.api.graph.timeline.domain.CrossChapterBoundaryProjection;

import java.util.List;

public record DefaultTemporalEdgeCreationResult(
        int inChapterEdgesCreated,
        int crossChapterEdgesCreated,
        List<CrossChapterBoundaryProjection> newlyCreatedCrossChapterBoundaries
) {
}
