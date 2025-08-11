package com.lorevault.api.graph.model;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Node("IngestionJob")
public class IngestionJobNode {

    @Id
    private UUID id;
    private UUID chapterId;
    private IngestionStatus currentStatus;
    private Integer progressPercent;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime completedAt; // reuse for simplicity (null until done)

    @Relationship(type = "HAS_STATUS")
    private List<StatusRecordNode> statusHistory;

    public IngestionJobNode() {}

    @PersistenceCreator
    public IngestionJobNode(UUID id, UUID chapterId, IngestionStatus currentStatus, Integer progressPercent,
                            LocalDateTime createdAt, LocalDateTime completedAt, List<StatusRecordNode> statusHistory) {
        this.id = id;
        this.chapterId = chapterId;
        this.currentStatus = currentStatus;
        this.progressPercent = progressPercent;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.statusHistory = statusHistory;
    }
}
