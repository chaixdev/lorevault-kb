package com.lorevault.api.content.relation;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * A single inter-entity relation claim extracted by the LLM during scene analysis.
 * <p>
 * RelationClaim nodes represent a raw, unresolved claim that two entities
 * participate in some relation, described by a free-text phrase from the LLM.
 * <p>
 * RelationClaim is NOT a Mention. Entity mentions share an entity-resolution lifecycle
 * (unresolved → resolved → aggregated). Relation claims have a different lifecycle
 * (unresolved → catalog-matched → promoted → projected). The {@code Mention} aggregate
 * label and Java interface are reserved for entity mentions; this node carries only
 * its own {@code RelationClaim} primary label.
 * <p>
 * Provenance anchors (sceneId, chapterId, bookId) are stored directly. Publication
 * coordinates are resolved by traversal to Scene → Chapter, not by denormalized
 * flat fields on this node.
 * <p>
 * Certainty is stored as a String ("Explicit", "StronglyImplied", "WeaklyImplied")
 * rather than the {@code CertaintyLevel} enum used by temporal edges. This preserves
 * the raw LLM output verbatim and avoids coupling the claim model to the enum's
 * ordinal weights. Phase 1 catalog matching will bridge via {@code CertaintyWeights}.
 */
@Node(primaryLabel = "RelationClaim")
public record RelationClaim(
        @Id UUID id,
        String relationName,
        String relationDescription,
        UUID catalogId,
        String definitionKey,
        String subjectKind,
        String subjectName,
        String objectKind,
        String objectName,
        String certainty,
        String evidenceText,
        String source,
        UUID sceneId,
        UUID chapterId,
        UUID bookId,
        Integer extractionIndex,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
