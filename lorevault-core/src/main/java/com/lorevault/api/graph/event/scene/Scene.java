package com.lorevault.api.graph.event.scene;

import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.library.chunk.Chunk;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a semantic scene within a chapter. Scenes are identified by AI analysis
 * based on shifts in time, location, or character focus. They serve as the intermediate
 * level in the hierarchy: Chapter -> Scene -> Chunk.
 *
 * In the current graph model, Scene is also the persisted event carrier. Scenes therefore
 * keep the Event label and event-oriented accessors while a broader Event -> Entity model
 * remains future work.
 *
 * Scenes contain the exact character coordinates within the chapter text and provide
 * contextual boundaries for the chunking process in v0.3.0+.
 */
@Data
@NoArgsConstructor
@Node("Scene")
public class Scene {
    public static final String EVENT_LABEL = "Event";
    public static final String POTENTIAL_SPLIT_SCENE_START_LABEL = "PotentialSplitSceneStart";
    public static final String POTENTIAL_SPLIT_SCENE_END_LABEL = "PotentialSplitSceneEnd";

    @Id
    private UUID id;

    /**
     * Foreign key referencing the parent Chapter (aggregate root)
     */
    private Chapter chapter;

    private UUID chapterId;

    @Property("stageId")
    private UUID stageId;

    /**
     * The sequential index of the scene within the chapter (0-based, matching AI output)
     */
    private Integer sceneIndex;

    /**
     * AI-generated summary describing the context/content of this scene
     */
    private String contextSummary;

    /**
     * Temporal relationship hint extracted during scene analysis.
     */
    private String chronology;

    /**
     * Certainty for chronology hint extracted during scene analysis.
     */
    private String chronologyCertainty;

    /**
     * Verbatim temporal marker extracted during scene analysis.
     */
    private String chronologyMarker;

    /**
     * Zero-indexed character position where this scene starts in the chapter text
     */
    @Property("startOffset")
    private Long startCharacterOffset;

    /**
     * Zero-indexed character position where this scene ends in the chapter text
     */
    @Property("endOffset")
    private Long endCharacterOffset;

    /**
     * The actual text content of this scene, materialized for traceability and context
     * This supports the distributed content storage model where scenes store their own
     * text content to enable independent access without requiring chapter materialization.
     */
    private String text;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @DynamicLabels
    private List<String> labels = new ArrayList<>(List.of(EVENT_LABEL));

    /**
     * Chunks that belong to this scene (v0.3.0+)
     */
    @Relationship(type = "HAS_CHUNK")
    private List<Chunk> chunks = new ArrayList<>();

    @PersistenceCreator
    public Scene(UUID id,
                 Integer sceneIndex,
                 Long startCharacterOffset,
                 Long endCharacterOffset,
                 String contextSummary,
                 String chronology,
                 String chronologyCertainty,
                 String chronologyMarker,
                 String text,
                 UUID chapterId,
                 List<String> labels,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 List<Chunk> chunks,
                 Chapter chapter) {
        this.id = id;
        this.sceneIndex = sceneIndex;
        this.startCharacterOffset = startCharacterOffset;
        this.endCharacterOffset = endCharacterOffset;
        this.contextSummary = contextSummary;
        this.chronology = chronology;
        this.chronologyCertainty = chronologyCertainty;
        this.chronologyMarker = chronologyMarker;
        this.text = text;
        this.chapterId = chapterId;
        this.labels = labels == null ? new ArrayList<>(List.of(EVENT_LABEL)) : labels;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.chunks = chunks == null ? new ArrayList<>() : chunks;
        this.chapter = chapter;
    }

    public Scene(UUID id,
                 Integer sceneIndex,
                 Long startCharacterOffset,
                 Long endCharacterOffset,
                 String contextSummary,
                 String text,
                 UUID chapterId,
                 List<String> labels,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 List<Chunk> chunks,
                 Chapter chapter) {
        this(id, sceneIndex, startCharacterOffset, endCharacterOffset, contextSummary,
                null, null, null, text, chapterId, labels, createdAt, updatedAt, chunks, chapter);
    }

    // =====================================
    // Business Methods
    // =====================================

    /**
     * Convenience method to get the length of the scene text
     */
    public long getTextLength() {
        return endCharacterOffset - startCharacterOffset;
    }

    /**
     * Extract the scene text from the provided chapter content
     */
    public String extractText(String chapterContent) {
        if (chapterContent == null || 
            startCharacterOffset >= chapterContent.length() || 
            endCharacterOffset > chapterContent.length()) {
            throw new IllegalArgumentException("Invalid character offsets for chapter content");
        }
        return chapterContent.substring(startCharacterOffset.intValue(), endCharacterOffset.intValue());
    }

    // --- Added for bidirectional chunk management ---
    public void addChunk(Chunk chunk) {
        if (chunk == null) return;
        if (chunks == null) chunks = new ArrayList<>();
        if (!chunks.contains(chunk)) {
            chunks.add(chunk);
            chunk.setScene(this);
        }
    }

    public void removeChunk(Chunk chunk) {
        if (chunk == null || chunks == null) return;
        if (chunks.remove(chunk)) {
            chunk.setScene(null);
        }
    }

    // --- Current event-carrier compatibility methods ---
    public java.util.UUID getEventId() {
        return id;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public Integer getSceneIndex() {
        return sceneIndex;
    }

    public Long getStartOffset() {
        return startCharacterOffset;
    }

    public Long getEndOffset() {
        return endCharacterOffset;
    }

}
