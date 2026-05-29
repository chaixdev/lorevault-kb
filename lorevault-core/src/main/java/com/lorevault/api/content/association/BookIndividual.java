package com.lorevault.api.content.association;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node(primaryLabel = "BookIndividual", labels = "BookEntity")
public record BookIndividual(
        @Id UUID id,
        UUID bookId,
        @Property("stageId") UUID stageId,
        String displayName,
        String normalizedName,
        Integer chapterIndividualCount,
        UUID representativeChapterIndividualId,
        UUID firstSeenChapterId,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
