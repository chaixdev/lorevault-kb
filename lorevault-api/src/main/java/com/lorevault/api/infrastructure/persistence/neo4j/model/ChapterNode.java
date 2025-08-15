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
@Node("Chapter")
public class ChapterNode {

    @Id
    private UUID id;

    private String universe;
    private String series; // optional
    private Integer bookNumber;
    private Integer chapterNumber;
    private String chapterTitle;
    @Property("rawText")
    private String rawText;
    private String contentHash;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Relationship(type = "HAS_SCENE")
    private List<SceneNode> scenes;

    @Relationship(type = "HAS_CHUNK")
    private List<ChunkNode> chunks; // optional direct linkage

    @PersistenceCreator
    public ChapterNode(UUID id, String universe, String series, Integer bookNumber,
                       Integer chapterNumber, String chapterTitle, String rawText, String contentHash,
                       LocalDateTime createdAt, LocalDateTime updatedAt, List<SceneNode> scenes, List<ChunkNode> chunks) {
        this.id = id;
        this.universe = universe;
        this.series = series;
        this.bookNumber = bookNumber;
        this.chapterNumber = chapterNumber;
        this.chapterTitle = chapterTitle;
        this.rawText = rawText;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.scenes = scenes;
        this.chunks = chunks;
    }

    public ChapterNode() {
    }
}
