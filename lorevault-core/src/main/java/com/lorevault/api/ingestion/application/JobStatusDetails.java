package com.lorevault.api.ingestion.application;

import com.lorevault.api.ingestion.domain.IngestionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobStatusDetails(
    UUID jobId,
    UUID chapterId,
    UUID bookId,
    IngestionStatus currentStatus,
    int progressPercent,
    boolean isComplete,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    List<StatusUpdate> recentUpdates,
    FailureDetails failureDetails
) {
    public record StatusUpdate(
        IngestionStatus status,
        String description,
        LocalDateTime timestamp,
        int progressPercent
    ) {}

    public record FailureDetails(
        String code,
        String message,
        String exceptionType,
        String stage,
        Map<String, String> additionalDetails
    ) {}
}
