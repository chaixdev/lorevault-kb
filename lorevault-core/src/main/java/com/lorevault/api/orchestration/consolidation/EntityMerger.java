package com.lorevault.api.orchestration.consolidation;

import java.util.List;
import java.util.UUID;

/**
 * Functional interface for merging a cluster of sources into a single
 * consolidated entity.
 *
 * <p>Each entity type (Individual, Location, Object, Collective) provides
 * its own implementation that determines how to pick representative field
 * values from the cluster members.
 *
 * @param <S>  the source type (e.g., IndividualMention, ChapterIndividual)
 * @param <T>  the target consolidated type (e.g., ChapterIndividual, BookIndividual)
 */
@FunctionalInterface
public interface EntityMerger<S, T> {

    /**
     * Merge a cluster of sources into a single consolidated entity.
     *
     * @param sources  the cluster members, in encounter order (first = representative)
     * @param ownerId  the ID of the owning entity (chapterId or bookId)
     * @return the consolidated entity
     */
    T merge(List<S> sources, UUID ownerId);
}