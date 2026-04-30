package com.lorevault.api.content.association;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node(primaryLabel = "ChapterEvent", labels = "ChapterEntity")
public record ChapterEvent(
        @Id UUID id,
        UUID chapterId,
        /**
         * The co-reference component representative ID (lexicographically smallest member UUID string).
         * Used as a stable lookup key after {@code saveAll} — positional correlation is not reliable.
         * Null for singleton events that had no SAME_EVENT links.
         */
        String componentId,
        String displayName,
        String normalizedName,
        String representativeEventType,
        Integer mentionCount,
        String aggregateCard,
        List<String> supportedAliases,
        List<String> supportedEventTypes,
        List<String> identityEvidence,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt,
        /**
         * Dense vector embedding of {@code aggregateCard}. Used for ANN-based event similarity
         * in Stage 4 of the ingestion pipeline. Null until the embedding pipeline has run.
         */
        double[] embedding,
        /**
         * SHA-256 of {@code modelId:aggregateCard} used to detect stale embeddings.
         * When {@code aggregateCard} changes or the embedding model rotates, the hash drifts
         * and the embedding is regenerated.
         */
        String embeddingHash,
        /** Timestamp when the embedding was last (re)computed. */
        LocalDateTime embeddedAt
) {}
