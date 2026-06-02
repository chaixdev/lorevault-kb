package com.lorevault.api.orchestration.submission;

import java.util.UUID;

public record IngestionSubmissionResult(UUID jobId, UUID chapterId) {
}
