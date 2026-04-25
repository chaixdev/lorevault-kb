package com.lorevault.api.content.entities;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node("ChapterEvent")
public record ChapterEvent(
        @Id UUID id,
        UUID chapterId,
        String displayName,
        String normalizedName,
        String representativeEventType,
        Integer mentionCount,
        String aggregateCard,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
