package com.lorevault.api.graph.location.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node(primaryLabel = "BookLocation", labels = "BookEntity")
public record BookLocation(
        @Id UUID id,
        UUID bookId,
        @Property("stageId") UUID stageId,
        String displayName,
        String normalizedName,
        List<String> aliases,
        Integer chapterLocationCount,
        UUID representativeChapterLocationId,
        UUID firstSeenChapterId,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
