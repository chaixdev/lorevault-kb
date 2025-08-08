package com.lorevault.api.domain.content;

import com.lorevault.api.domain.shared.PublicationCoordinates;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.stream.Collectors;

/**
 * The root entity representing a single, complete chapter from a source book.
 * Contains the full raw text and high-level metadata. Acts as the "source of truth"
 * from which scenes and chunks are derived.
 */
@Entity
@Table(
    name = "chapters",
    indexes = {
        @Index(name = "idx_chapters_coordinates", columnList = "universe, series, bookNumber, chapterNumber"),
        @Index(name = "idx_chapters_content_hash", columnList = "contentHash")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Embedded coordinates object defining the chapter's position in the published text corpus
     */
    @Embedded
    @Valid
    @NotNull
    private PublicationCoordinates coordinates;

    /**
     * The title of the chapter
     */
    @Column(nullable = false)
    @NotBlank
    private String chapterTitle;

    /**
     * The full, unmodified chapter text
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    @NotBlank
    private String rawText;

    /**
     * A SHA-256 hash of rawText for deduplication
     */
    @Column(nullable = false, unique = true)
    @NotBlank
    private String contentHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Scenes within this chapter (v0.3.0+)
     * Ordered by scene index
     */
    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sceneIndex ASC")
    private List<Scene> scenes = new ArrayList<>();

    /**
     * All chunks within this chapter
     * For v0.2.0: Direct chapter → chunk relationship
     * For v0.3.0+: Includes both legacy direct chunks and scene-based chunks
     */
    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("chunkNumberInChapter ASC")
    private List<Chunk> chunks = new ArrayList<>();

    // =====================================
    // Aggregate Root Methods for Scenes
    // =====================================

    /**
     * Get read-only view of scenes
     */
    public List<Scene> getScenes() {
        return Collections.unmodifiableList(scenes);
    }

    /**
     * Add a scene to this chapter
     */
    public Scene addScene(int sceneIndex, long startCharacterOffset, long endCharacterOffset, String contextSummary) {
        Scene scene = new Scene();
        scene.setChapter(this);
        scene.setSceneIndex(sceneIndex);
        scene.setStartCharacterOffset(startCharacterOffset);
        scene.setEndCharacterOffset(endCharacterOffset);
        scene.setContextSummary(contextSummary);
        
        scenes.add(scene);
        return scene;
    }

    /**
     * Remove a scene from this chapter
     */
    public void removeScene(Scene scene) {
        if (scene != null && scenes.contains(scene)) {
            scenes.remove(scene);
            scene.setChapter(null);
        }
    }

    /**
     * Clear all scenes
     */
    public void clearScenes() {
        scenes.forEach(scene -> scene.setChapter(null));
        scenes.clear();
    }

    /**
     * Get scene count
     */
    public int getSceneCount() {
        return scenes.size();
    }

    // =====================================
    // Aggregate Root Methods for Chunks
    // =====================================

    /**
     * Get read-only view of chunks
     */
    public List<Chunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Add a chunk to this chapter
     */
    public Chunk addChunk(int chunkNumber, int startChar, int endChar, String contentHash) {
        Chunk chunk = new Chunk();
        chunk.setChapter(this);
        chunk.setChunkNumberInChapter(chunkNumber);
        chunk.setStartCharInChapter(startChar);
        chunk.setEndCharInChapter(endChar);
        chunk.setContentHash(contentHash);
        
        chunks.add(chunk);
        return chunk;
    }

    /**
     * Add a chunk to a specific scene within this chapter
     */
    public Chunk addChunkToScene(Scene scene, int chunkNumber, int startChar, int endChar, String contentHash) {
        if (scene == null || !scenes.contains(scene)) {
            throw new IllegalArgumentException("Scene must belong to this chapter");
        }
        
        Chunk chunk = addChunk(chunkNumber, startChar, endChar, contentHash);
        chunk.setScene(scene);
        scene.addChunk(chunk);
        
        return chunk;
    }

    /**
     * Remove a chunk from this chapter
     */
    public void removeChunk(Chunk chunk) {
        if (chunk != null && chunks.contains(chunk)) {
            chunks.remove(chunk);
            chunk.setChapter(null);
            
            // Also remove from scene if associated
            if (chunk.getScene() != null) {
                chunk.getScene().removeChunk(chunk);
                chunk.setScene(null);
            }
        }
    }

    /**
     * Clear all chunks
     */
    public void clearChunks() {
        chunks.forEach(chunk -> {
            chunk.setChapter(null);
            if (chunk.getScene() != null) {
                chunk.getScene().removeChunk(chunk);
                chunk.setScene(null);
            }
        });
        chunks.clear();
    }

    /**
     * Get chunk count
     */
    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * Get chunks for a specific scene
     */
    public List<Chunk> getChunksForScene(Scene scene) {
        if (scene == null || !scenes.contains(scene)) {
            return Collections.emptyList();
        }
        
        return chunks.stream()
                .filter(chunk -> scene.equals(chunk.getScene()))
                .collect(Collectors.toList());
    }
}
