package com.lorevault.api.content;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Individual")
public record Individual(
        @Id UUID id,
        boolean provisional,
        String source,
        String displayName,
        List<String> aliases,
        String description,
        String age,
        String physicalProperties,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
