package com.lorevault.api.content.relation;

import java.time.LocalDateTime;
import java.util.UUID;

import com.lorevault.api.content.mention.Mention;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * A single inter-entity relation claim extracted by the LLM during scene analysis.
 * <p>
 * RelationClaim nodes represent a raw, unresolved claim that two entities
 * participate in some relation, described by a free-text phrase from the LLM.
 * They carry the {@code Mention} aggregate label so they participate in
 * scene-level mention indexing alongside entity mention types.
 * <p>
 * Certainty is stored as a String ("Explicit", "StronglyImplied", "WeaklyImplied")
 * rather than the {@code CertaintyLevel} enum used by temporal edges. This preserves
 * the raw LLM output verbatim and avoids coupling the claim model to the enum's
 * ordinal weights. Phase 1 catalog matching will bridge via {@code CertaintyWeights}.
 */
@Node(primaryLabel = "RelationClaim", labels = "Mention")
public record RelationClaim(
        @Id UUID id,
        String relationName,
        String relationDescription,
        String provisionalRelTypeId,
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
        String resolutionStatus,
        // Publication coordinates — populated during book-level processing
        String pubUniverse,
        String pubSeries,
        Integer pubBookNumber,
        Integer pubChapterNumber,
        Integer pubSceneIndex,
        String pubKey,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) implements Mention {

    @Override
    public String displayName() {
        return relationName;
    }

    @Override
    public String normalizedName() {
        return provisionalRelTypeId;
    }
}
