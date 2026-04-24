package com.lorevault.api.content.timeline.application;

import java.util.UUID;

public record TemporalEdgeProvenance(
        UUID jobId,
        UUID chapterId,
        UUID statusRecordId,
        UUID llmCallRecordId
) {
}
