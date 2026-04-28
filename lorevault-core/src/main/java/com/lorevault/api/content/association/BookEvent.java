package com.lorevault.api.content.association;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node("BookEvent")
public record BookEvent(
        @Id UUID id,
        UUID bookId,
        String displayName,
        String normalizedName,
        String representativeEventType,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
