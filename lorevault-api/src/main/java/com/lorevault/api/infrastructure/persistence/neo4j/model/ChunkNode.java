package com.lorevault.api.infrastructure.persistence.neo4j.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Node("Chunk")
public class ChunkNode {

    @Id
    private UUID id;

    private Integer chunkNumberInChapter;
    private Integer startCharInChapter;
    private Integer endCharInChapter;
    private String contentHash;

    // Embedding fields (Phase 1)
    private double[] embedding;          // Vector values (null until generated)
    private String embeddingHash;        // SHA256(modelId:contentHash) for idempotency
    private LocalDateTime embeddedAt;    // Timestamp when embedding was generated

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    public ChunkNode(UUID id,
                     Integer chunkNumberInChapter,
                     Integer startCharInChapter,
                     Integer endCharInChapter,
                     String contentHash,
                     double[] embedding,
                     String embeddingHash,
                     LocalDateTime embeddedAt,
                     LocalDateTime createdAt,
                     LocalDateTime updatedAt) {
        this.id = id;
        this.chunkNumberInChapter = chunkNumberInChapter;
        this.startCharInChapter = startCharInChapter;
        this.endCharInChapter = endCharInChapter;
        this.contentHash = contentHash;
        this.embedding = embedding;
        this.embeddingHash = embeddingHash;
        this.embeddedAt = embeddedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ChunkNode() {}
}
