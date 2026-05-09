package com.lorevault.api.content.association;

/**
 * DTO carrying individual-mention resolution candidate metadata returned by
 * {@link ChapterIndividualGraphRepository#findResolutionCandidates(java.util.UUID)}.
 *
 * <p>Implemented as a Java record (not a Spring Data projection interface) to avoid
 * Spring Data Neo4j's {@code DirectFieldAccessFallbackBeanWrapper} attempting to
 * map the result columns onto the repository's domain entity ({@code ChapterIndividual})
 * instead of the projection interface. Using a concrete record type sidesteps the issue
 * entirely because Spring Data Neo4j will construct the record via its canonical
 * constructor, matching constructor parameters to the Cypher return columns by name.
 *
 * @param displayName   the representative display name for this group of mentions
 * @param normalizedName the normalized name shared by all mentions in this group
 * @param mentionCount   the number of mentions sharing this normalized name
 */
public record ChapterIndividualCandidate(
        String displayName,
        String normalizedName,
        Long mentionCount
) implements ChapterIndividualCandidateView {

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getNormalizedName() {
        return normalizedName;
    }

    @Override
    public Long getMentionCount() {
        return mentionCount;
    }
}
