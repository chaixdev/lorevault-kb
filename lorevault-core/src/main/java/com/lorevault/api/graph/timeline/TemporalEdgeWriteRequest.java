package com.lorevault.api.graph.timeline;

import java.util.UUID;

public record TemporalEdgeWriteRequest(
        UUID fromSceneId,
        UUID toSceneId,
        String temporalType,
        String certainty,
        String evidence,
        String timelineMarker,
        TemporalEdgeProvenance provenance
) {
}
