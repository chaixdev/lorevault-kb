package com.lorevault.api.graph.event.consolidation.chapter;

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
