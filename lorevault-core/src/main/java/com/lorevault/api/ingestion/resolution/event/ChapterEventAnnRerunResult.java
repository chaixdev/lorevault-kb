package com.lorevault.api.ingestion.resolution.event;

import java.util.UUID;

public record ChapterEventAnnRerunResult(
        boolean success,
        SelectedScope selectedScope,
        int triggeredChapterCount,
        UUID jobId,
        UUID correlationId,
        String message
) {
    public record SelectedScope(UUID universeId, UUID bookId, UUID chapterId) {
    }
}
