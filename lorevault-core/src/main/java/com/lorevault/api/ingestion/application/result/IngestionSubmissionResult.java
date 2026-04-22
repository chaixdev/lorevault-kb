package com.lorevault.api.ingestion.application.result;

import java.util.UUID;

public record IngestionSubmissionResult(UUID jobId, UUID chapterId) {
}
