package com.lorevault.api.infrastructure.persistence.neo4j.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Node("IngestionJob")
public class IngestionJobNode {

    @Id
    private UUID id;
    private UUID chapterId;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime completedAt; // null until done

    @Relationship(type = "HAS_INITIAL_STATUS")
    private StatusRecordNode initialStatusRecord;

    @Relationship(type = "HAS_CURRENT_STATUS")
    private StatusRecordNode currentStatusRecord;

    @Relationship(type = "TRACKS_INGESTION_PROGRESS_FOR", direction = Relationship.Direction.OUTGOING)
    private ChapterNode chapter;

    public IngestionJobNode() {}

    @PersistenceCreator
    public IngestionJobNode(UUID id, UUID chapterId, StatusRecordNode initialStatusRecord, StatusRecordNode currentStatusRecord,
                            LocalDateTime createdAt, LocalDateTime completedAt, ChapterNode chapter) {
        this.id = id;
        this.chapterId = chapterId;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.initialStatusRecord = initialStatusRecord;
        this.currentStatusRecord = currentStatusRecord;
        this.chapter = chapter;
    }
}
