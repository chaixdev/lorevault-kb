package com.lorevault.api.ingestion.resolution.event;

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
