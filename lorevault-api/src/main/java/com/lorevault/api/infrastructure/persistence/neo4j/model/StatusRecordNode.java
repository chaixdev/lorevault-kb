package com.lorevault.api.infrastructure.persistence.neo4j.model;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Node("StatusRecord")
public class StatusRecordNode {

    @Id
    private UUID id;
    private UUID jobId; // explicit top-level property used in Cypher queries
    private IngestionStatus status;
    private String stepDescription;
    private Integer progressPercent;

    @CreatedDate
    private LocalDateTime timestamp;

    @Relationship("HAS_NEXT_STATUS")
    StatusRecordNode next;

    @PersistenceCreator
    public StatusRecordNode(UUID id, UUID jobId, LocalDateTime timestamp, IngestionStatus status, String stepDescription, Integer progressPercent,
                            StatusRecordNode next) {
        this.id = id;
        this.jobId = jobId;
        this.status = status;
        this.stepDescription = stepDescription;
        this.progressPercent = progressPercent;
        this.timestamp = timestamp;
        this.next = next;
    }

    public StatusRecordNode() {}
}
