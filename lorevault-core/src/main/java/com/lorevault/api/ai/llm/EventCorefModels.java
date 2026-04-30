package com.lorevault.api.ai.llm;

import java.util.List;

/**
 * Domain model records for the Stage 2 event co-reference pass.
 *
 * <p>Stage 2 drives rolling-triad windows over persisted {@code EventMention} nodes
 * and asks an LLM: "do any of these mentions refer to the same real-world event?"
 * These records carry the input/output contract for that LLM call.</p>
 */
public final class EventCorefModels {

    private EventCorefModels() {}

    // -------------------------------------------------------------------------
    // LLM input
    // -------------------------------------------------------------------------

    /**
     * Structured representation of a single EventMention for the co-reference prompt.
     * Only persisted mention fields are included — no raw chapter text.
     */
    public record CorefMentionInput(
            String mentionId,
            String displayName,
            String normalizedName,
            String eventType,
            String description,
            String sceneRelativeRelation,
            String certainty,
            String evidence
    ) {}

    // -------------------------------------------------------------------------
    // LLM output (structured response — Spring AI entity binding)
    // -------------------------------------------------------------------------

    /**
     * A positive same-event group produced by the LLM for mentions in the window.
     *
     * @param mentionIds  ids of two or more mentions judged to refer to the same real-world event
     * @param confidence  model's self-reported confidence in [0.0, 1.0]
     * @param rationale   brief natural-language explanation (for audit/debug only)
     */
    public record CorefSameEventGroup(
            List<String> mentionIds,
            double confidence,
            String rationale
    ) {}

    /**
     * Full structured response from the LLM for one co-reference window.
     * Contains only positive same-event groups. Mentions omitted from all groups are treated as
     * unmerged for this window; no negative pair judgments are requested or persisted.
     */
    public record CorefWindowResponse(List<CorefSameEventGroup> sameEventGroups) {}

    // -------------------------------------------------------------------------
    // Pass result
    // -------------------------------------------------------------------------

    /**
     * Summary of a completed Stage 2 co-reference pass for one chapter.
     *
     * @param chapterId      chapter that was processed
     * @param passId         unique id for this pass (used as metadata on SAME_EVENT links)
     * @param model          model id that was used
     * @param windowsRun     number of rolling-triad windows executed
     * @param linksCreated   number of SAME_EVENT links written
     * @param failedWindowCount number of windows that failed and were skipped
     */
    public record CorefPassResult(
            java.util.UUID chapterId,
            String passId,
            String model,
            int windowsRun,
            int linksCreated,
            int failedWindowCount
    ) {}
}
