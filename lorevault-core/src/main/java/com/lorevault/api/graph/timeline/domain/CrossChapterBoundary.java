package com.lorevault.api.graph.timeline.domain;

import java.util.UUID;

/**
 * DTO carrying cross-chapter boundary metadata returned by
 * {@link com.lorevault.api.graph.timeline.infrastructure.TemporalEdgeWriteRepository#mergeCrossChapterDefaultEdges(UUID)}.
 *
 * <p>Implemented as a Java record (not a Spring Data projection interface) to avoid
 * Spring Data Neo4j's {@code DirectFieldAccessFallbackBeanWrapper} attempting to
 * map the result columns onto the repository's domain entity ({@code Scene}) instead
 * of the projection interface. Using a concrete record type sidesteps the issue
 * entirely because Spring Data Neo4j will construct the record via its canonical
 * constructor, matching constructor parameters to the Cypher return columns by name.
 *
 * @param previousChapterId the chapter that ends with the "last scene"
 * @param nextChapterId     the chapter that starts with the "first scene"
 * @param previousSceneId   the last scene in the earlier chapter
 * @param nextSceneId       the first scene in the later chapter
 */
public record CrossChapterBoundary(
        UUID previousChapterId,
        UUID nextChapterId,
        UUID previousSceneId,
        UUID nextSceneId
) implements CrossChapterBoundaryProjection {

    @Override
    public UUID getPreviousChapterId() {
        return previousChapterId;
    }

    @Override
    public UUID getNextChapterId() {
        return nextChapterId;
    }

    @Override
    public UUID getPreviousSceneId() {
        return previousSceneId;
    }

    @Override
    public UUID getNextSceneId() {
        return nextSceneId;
    }
}
