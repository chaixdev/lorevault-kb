package com.lorevault.api.content.association;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node(primaryLabel = "ChapterIndividual", labels = "ChapterEntity")
public record ChapterIndividual(
        @Id UUID id,
        UUID chapterId,
        @Property("stageId") UUID stageId,
        String displayName,
        String normalizedName,
        Integer mentionCount,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
