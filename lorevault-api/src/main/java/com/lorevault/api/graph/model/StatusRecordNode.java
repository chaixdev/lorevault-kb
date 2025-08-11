package com.lorevault.api.graph.model;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

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

    @PersistenceCreator
    public StatusRecordNode(UUID id, UUID jobId, IngestionStatus status, String stepDescription, Integer progressPercent,
                            LocalDateTime timestamp) {
        this.id = id;
        this.jobId = jobId;
        this.status = status;
        this.stepDescription = stepDescription;
        this.progressPercent = progressPercent;
        this.timestamp = timestamp;
    }

    public StatusRecordNode() {}
}
