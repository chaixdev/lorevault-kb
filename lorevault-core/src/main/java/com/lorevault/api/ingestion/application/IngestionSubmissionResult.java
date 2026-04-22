package com.lorevault.api.ingestion.application;

import java.util.UUID;

public record IngestionSubmissionResult(UUID jobId, UUID chapterId) {
}
