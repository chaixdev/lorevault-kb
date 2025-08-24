package com.lorevault.api.application.port;

import com.lorevault.api.domain.content.Scene;

import java.util.AbstractMap;
import java.util.List;
import java.util.UUID;

/**
 * Read-side port for retrieving the minimal data needed to compute
 * deterministic event ordering (scenes and precedence edges).
 */
public interface EventOrderingPort {

    /**
     * Find all scene events for a chapter.
     * Returned list order is not relied upon by the ordering service.
     */
    List<Scene> findChapterScenes(UUID chapterId);

    /**
     * Find directed precedence edges among scene events within a chapter.
     * Edges represent strict "earlier -> later" constraints.
     * Implementations SHOULD union both :MEETS and :TEMPORAL relations.
     */
    List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId);

    /**
     * Return Chapter IDs for a book where chapterNumber <= uptoChapterNumber,
     * ordered by chapterNumber ascending.
     */
    List<UUID> findBookChapterIdsUpTo(UUID bookId, int uptoChapterNumber);
}
