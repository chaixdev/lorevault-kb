package com.lorevault.api.graph.collective.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.lorevault.api.graph.mention.Mention;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node(primaryLabel = "CollectiveMention", labels = "Mention")
public record CollectiveMention(
        @Id UUID id,
        String source,
        String displayName,
        String normalizedName,
        List<String> aliases,
        String collectiveType,
        String certainty,
        String evidence,
        @Property("stageId") UUID stageId,
        UUID sceneId,
        UUID chapterId,
        UUID bookId,
        String resolutionStatus,
        Integer extractionIndex,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) implements Mention {}
