package com.lorevault.api.graph.timeline;

import java.util.UUID;

public record TemporalEdgeProvenance(
        UUID jobId,
        UUID chapterId,
        UUID stageId,
        UUID llmCallRecordId
) {
}
