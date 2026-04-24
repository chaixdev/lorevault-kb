package com.lorevault.api.content.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node("ObjectMention")
public record ObjectMention(
        @Id UUID id,
        String source,
        String displayName,
        String normalizedName,
        List<String> aliases,
        String type,
        String material,
        String purpose,
        String description,
        UUID sceneId,
        UUID chapterId,
        UUID bookId,
        String resolutionStatus,
        Integer extractionIndex,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) implements Mention {}
