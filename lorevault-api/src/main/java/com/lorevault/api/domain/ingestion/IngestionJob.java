package com.lorevault.api.domain.ingestion;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("IngestionJob")
public class IngestionJob {
    @Id
    private UUID id;

    private UUID chapterId;

    @Relationship(type = "HAS_CURRENT_STATUS")
    private StatusRecord currentStatus;

    @CreatedDate
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
