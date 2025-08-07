package com.lorevault.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a semantic scene within a chapter. Scenes are identified by AI analysis
 * based on shifts in time, location, or character focus. They serve as the intermediate
 * level in the hierarchy: Chapter -> Scene -> Chunk.
 * 
 * Scenes contain the exact character coordinates within the chapter text and provide
 * contextual boundaries for the chunking process in v0.3.0+.
 */
@Entity
@Table(
    name = "scenes",
    indexes = {
        @Index(name = "idx_scenes_chapter", columnList = "chapterId"),
        @Index(name = "idx_scenes_position", columnList = "chapterId, sceneIndex"),
        @Index(name = "idx_scenes_offsets", columnList = "startCharacterOffset, endCharacterOffset")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_scenes_chapter_index", columnNames = {"chapterId", "sceneIndex"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Foreign key referencing the parent Chapter (aggregate root)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    @NotNull
    private Chapter chapter;

    /**
     * The sequential index of the scene within the chapter (0-based, matching AI output)
     */
    @Column(name = "scene_index", nullable = false)
    @NotNull
    @PositiveOrZero
    private Integer sceneIndex;

    /**
     * AI-generated summary describing the context/content of this scene
     */
    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    /**
     * Zero-indexed character position where this scene starts in the chapter text
     */
    @Column(name = "start_character_offset", nullable = false)
    @NotNull
    @PositiveOrZero
    private Long startCharacterOffset;

    /**
     * Zero-indexed character position where this scene ends in the chapter text
     */
    @Column(name = "end_character_offset", nullable = false)
    @NotNull
    @PositiveOrZero
    private Long endCharacterOffset;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Chunks that belong to this scene (v0.3.0+)
     */
    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Chunk> chunks = new ArrayList<>();

    // =====================================
    // Collection Management Methods
    // =====================================

    /**
     * Get read-only view of chunks in this scene
     */
    public List<Chunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Add a chunk to this scene (internal method - should be called via Chapter aggregate)
     */
    protected void addChunk(Chunk chunk) {
        if (chunk != null && !chunks.contains(chunk)) {
            chunks.add(chunk);
        }
    }

    /**
     * Remove a chunk from this scene (internal method - should be called via Chapter aggregate)
     */
    protected void removeChunk(Chunk chunk) {
        chunks.remove(chunk);
    }

    /**
     * Get chunk count for this scene
     */
    public int getChunkCount() {
        return chunks.size();
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
}
