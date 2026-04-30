package com.lorevault.api.content.association;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node(primaryLabel = "ChapterObject", labels = "ChapterEntity")
public record ChapterObject(
        @Id UUID id,
        UUID chapterId,
        String displayName,
        String normalizedName,
        List<String> aliases,
        String type,
        String material,
        String purpose,
        String description,
        Integer mentionCount,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
