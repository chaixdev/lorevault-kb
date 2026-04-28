package com.lorevault.api.ai.llm;

import java.util.UUID;

/**
 * Domain model records for ChapterEvent semantic merge verification (Stage 5)
 * and BookEvent reduction inputs (Stage 6).
 */
public final class EventMergeModels {

    private EventMergeModels() {}

    public enum MergeDecision {
        MERGE,
        KEEP_SEPARATE,
        UNRESOLVED;

        public static MergeDecision from(String rawDecision) {
            if (rawDecision == null || rawDecision.isBlank()) {
                return UNRESOLVED;
            }
            return switch (rawDecision.trim().toUpperCase()) {
                case "MERGE" -> MERGE;
                case "KEEP_SEPARATE" -> KEEP_SEPARATE;
                default -> UNRESOLVED;
            };
        }
    }

    /**
     * Structured Stage 5 response for one candidate pair.
     */
    public record EventMergePairResponse(
            String decision,
            Double confidence,
            String rationale
    ) {}

    /**
     * Verification record produced by Stage 5 for one ANN candidate pair.
     */
    public record EventMergeVerification(
            UUID eventId1,
            UUID eventId2,
            MergeDecision decision,
            double confidence,
            String rationale
    ) {}

    /**
     * Stage 6 input record representing a verified MERGE decision.
     */
    public record EventMergeDecision(
            UUID eventId1,
            UUID eventId2,
            double confidence
    ) {}
}
