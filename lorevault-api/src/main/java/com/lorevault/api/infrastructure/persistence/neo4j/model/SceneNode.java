package com.lorevault.api.infrastructure.persistence.neo4j.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Node("Scene")
public class SceneNode {

    @Id
    private UUID id;

    private Integer sceneIndex;
    private Long startOffset;
    private Long endOffset;
    private String contextSummary;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Relationship(type = "HAS_CHUNK")
    private List<ChunkNode> chunks;

    @PersistenceCreator
    public SceneNode(UUID id, Integer sceneIndex, Long startOffset, Long endOffset, String contextSummary,
                     LocalDateTime createdAt, LocalDateTime updatedAt, List<ChunkNode> chunks) {
        this.id = id;
        this.sceneIndex = sceneIndex;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.contextSummary = contextSummary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.chunks = chunks;
    }

    public SceneNode() {}
}
