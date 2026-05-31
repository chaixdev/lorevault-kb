package com.lorevault.api.graph.event.persistence;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node(primaryLabel = "BookEvent", labels = {"BookEntity", "EventNode"})
public record BookEvent(
        @Id UUID id,
        UUID bookId,
        @Property("stageId") UUID stageId,
        String displayName,
        String normalizedName,
        String representativeEventType,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
