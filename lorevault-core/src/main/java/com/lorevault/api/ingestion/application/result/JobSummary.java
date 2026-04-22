package com.lorevault.api.ingestion.application.result;

import com.lorevault.api.ingestion.domain.IngestionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobSummary(
    UUID jobId,
    UUID chapterId,
    UUID bookId,
    String chapterTitle,
    String universe,
    String series,
    Integer bookNumber,
    Integer chapterNumber,
    IngestionStatus status,
    int progress,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}
