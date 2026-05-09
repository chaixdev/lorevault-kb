package com.lorevault.api.content.association;

/**
 * Read model for individual-mention resolution candidates within a chapter.
 * Carries the representative display name, normalized name, and mention count
 * for each group of individual mentions sharing the same normalized name.
 */
public interface ChapterIndividualCandidateView {
    String getDisplayName();

    String getNormalizedName();

    Long getMentionCount();
}
