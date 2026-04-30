package com.lorevault.api.ingestion.submission;

import java.util.UUID;

public record IngestionSubmissionResult(UUID jobId, UUID chapterId) {
}
