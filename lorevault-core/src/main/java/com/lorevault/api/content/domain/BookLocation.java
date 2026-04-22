package com.lorevault.api.content.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node("BookLocation")
public record BookLocation(
        @Id UUID id,
        UUID bookId,
        String displayName,
        String normalizedName,
        List<String> aliases,
        Integer chapterLocationCount,
        UUID representativeChapterLocationId,
        UUID firstSeenChapterId,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
