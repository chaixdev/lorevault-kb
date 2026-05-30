package com.lorevault.api.library.chunk;

import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.graph.event.scene.Scene;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("Chunk")
public class Chunk {
    public static final String VECTOR_INDEX_NAME = "chunk_embedding_idx";

    @Id
    private UUID id;

    @Transient
    private Chapter chapter;

    @Transient
    private Scene scene;

    @Property("stageId")
    private UUID stageId;
    private Integer chunkNumberInChapter;
    private Integer startCharInChapter;
    private Integer endCharInChapter;
    
    /**
     * The actual text content of this chunk, materialized for embedding independence
     * This supports the distributed content storage model where chunks are decoupled
     * from their source chapter and store their own embeddable text content.
     */
    private String text;
    
    private String contentHash;
    private double[] embedding;
    private String embeddingHash;
    private LocalDateTime embeddedAt;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    public Chunk(UUID id,
                 Integer chunkNumberInChapter,
                 Integer startCharInChapter,
                 Integer endCharInChapter,
                 String contentHash,
                 String text,
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
        this.text = text;
        this.embedding = embedding;
        this.embeddingHash = embeddingHash;
        this.embeddedAt = embeddedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getLength() { return endCharInChapter - startCharInChapter; }

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }
}
