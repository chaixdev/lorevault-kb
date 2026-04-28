package com.lorevault.api.content.mention;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node("IndividualMention")
public record IndividualMention(
        @Id UUID id,
        String source,
        String displayName,
        String normalizedName,
        List<String> aliases,
        String activity,
        String age,
        String physicalProperties,
        UUID sceneId,
        UUID chapterId,
        UUID bookId,
        String resolutionStatus,
        Integer extractionIndex,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) implements Mention {}
