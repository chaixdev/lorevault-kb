package com.lorevault.api.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Narrow port for chapter lookups needed by services building scene triads.
 */
public interface ChapterLookupPort {
    /**
     * Return Chapter IDs for a book where chapterNumber <= uptoChapterNumber, ordered by chapterNumber.
     */
    List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber);
}
