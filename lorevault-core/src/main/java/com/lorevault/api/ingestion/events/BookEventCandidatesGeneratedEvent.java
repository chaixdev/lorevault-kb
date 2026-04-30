package com.lorevault.api.ingestion.events;

import java.util.UUID;
import lombok.Getter;

/**
 * Published by Stage 4 (ChapterEventEmbeddingHandler) after ANN candidate pairs have been
 * generated for a chapter.  Carries enough summary data for the fan-in coordinator to log
 * and route the completion signal.
 *
 * <p>This is one of the required fan-in branches in {@code IngestionCompletionCoordinator}.
 */
@Getter
public class BookEventCandidatesGeneratedEvent extends IngestionEvent {

    private final UUID bookId;
    /** Number of ChapterEvent nodes that were embedded in this run (0 if all were up-to-date). */
    private final int embeddedCount;
    /** Number of candidate pairs generated for this chapter. */
    private final int candidatePairCount;
    /** Number of BookEvent nodes created for this chapter in Stage 6. */
    private final int bookEventsCreated;

    public BookEventCandidatesGeneratedEvent(
            Object source,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            int embeddedCount,
            int candidatePairCount,
            int bookEventsCreated
    ) {
        super(source, jobId, chapterId);
        this.bookId = bookId;
        this.embeddedCount = embeddedCount;
        this.candidatePairCount = candidatePairCount;
        this.bookEventsCreated = bookEventsCreated;
    }

    public BookEventCandidatesGeneratedEvent(
            Object source,
            UUID jobId,
            UUID correlationId,
            UUID chapterId,
            UUID bookId,
            int embeddedCount,
            int candidatePairCount,
            int bookEventsCreated
    ) {
        super(source, jobId, correlationId, chapterId);
        this.bookId = bookId;
        this.embeddedCount = embeddedCount;
        this.candidatePairCount = candidatePairCount;
        this.bookEventsCreated = bookEventsCreated;
    }

    @Override
    public String getEventType() {
        return "BOOK_EVENT_CANDIDATES_GENERATED";
    }
}
