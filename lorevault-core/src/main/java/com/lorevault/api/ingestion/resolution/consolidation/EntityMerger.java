package com.lorevault.api.ingestion.resolution.consolidation;

import java.util.List;
import java.util.UUID;

/**
 * Merges a cluster of source entities into a single target entity.
 *
 * <p>Each entity type and lifecycle level provides its own implementation.
 * The merger is responsible for:
 * <ul>
 *   <li>Creating the target entity with the appropriate {@code ownerId}
 *       (chapterId or bookId)</li>
 *   <li>Collapsing optional fields (first non-blank wins) if applicable</li>
 *   <li>Unioning alias sets</li>
 *   <li>Deriving mention/count fields from the cluster size</li>
 * </ul>
 *
 * @param <S> the source entity type (e.g. {@code LocationMention},
 *            {@code ChapterLocation})
 * @param <T> the target entity type (e.g. {@code ChapterLocation},
 *            {@code BookLocation})
 */
@FunctionalInterface
public interface EntityMerger<S, T> {

    /**
     * Merge a cluster of source entities into a single target entity.
     *
     * @param sources  the cluster of source entities belonging to the same
     *                 real-world entity (non-empty)
     * @param ownerId  the owning entity ID (chapterId at the chapter level,
     *                 bookId at the book level)
     * @return the merged target entity
     */
    T merge(List<S> sources, UUID ownerId);
}
