package com.lorevault.api.ingestion.resolution.event;

import java.util.UUID;

public record TemporalEdgeProvenance(
        UUID jobId,
        UUID chapterId,
        UUID statusRecordId,
        UUID llmCallRecordId
) {
}
