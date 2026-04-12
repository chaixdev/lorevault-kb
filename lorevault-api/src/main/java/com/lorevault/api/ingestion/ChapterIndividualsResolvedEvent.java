package com.lorevault.api.ingestion;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ChapterIndividualsResolvedEvent extends IngestionEvent {

    private final UUID bookId;
    private final boolean processed;
    private final int mentionCount;
    private final int chapterIndividualCount;

    public ChapterIndividualsResolvedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            boolean processed,
            int mentionCount,
            int chapterIndividualCount
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.processed = processed;
        this.mentionCount = mentionCount;
        this.chapterIndividualCount = chapterIndividualCount;
    }

    @Override
    public String getEventType() {
        return "CHAPTER_INDIVIDUALS_RESOLVED";
    }
}
