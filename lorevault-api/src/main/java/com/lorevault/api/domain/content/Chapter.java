package com.lorevault.api.domain.content;

import com.lorevault.api.dto.shared.PublicationCoordinates;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {
    private UUID id;

    /**
     * Stable UUID references for graph relationships
     */
    private UUID bookId;
    private UUID universeId;  // denormalized for fast filtering
    private UUID seriesId;    // denormalized for fast filtering, nullable for standalone books

    /**
     * Embedded coordinates object defining the chapter's position in the published text corpus.
     * Essential for spoiler gating (ordering) and human-readable display.
     */
    private PublicationCoordinates coordinates;

    /**
     * The title of the chapter
     */
    private String chapterTitle;

    /**
     * The full, unmodified chapter text
     */
    private String rawText;

    /**
     * A SHA-256 hash of rawText for deduplication
     */
    private String contentHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Scenes within this chapter, ordered by scene index
     */
    private List<Scene> scenes = new ArrayList<>();

    /**
     * All chunks within this chapter
     */
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

    // =====================================
    // Convenience Methods for Publication Coordinates
    // =====================================

    /**
     * Get the universe name from coordinates
     */
    public String getUniverse() {
        return coordinates != null ? coordinates.getUniverse() : null;
    }

    /**
     * Get the series name from coordinates
     */
    public String getSeries() {
        return coordinates != null ? coordinates.getSeries() : null;
    }

    /**
     * Get the book number from coordinates
     */
    public Integer getBookNumber() {
        return coordinates != null ? coordinates.getBookNumber() : null;
    }

    /**
     * Get the chapter number from coordinates
     */
    public Integer getChapterNumber() {
        return coordinates != null ? coordinates.getChapterNumber() : null;
    }

    // =====================================
    // Factory Methods for UUID-based Creation
    // =====================================

    /**
     * Create a chapter with full UUID references for graph relationships.
     * PublicationCoordinates remain essential for spoiler gating and display.
     */
    public static Chapter createWithReferences(UUID bookId, UUID universeId, UUID seriesId,
                                             PublicationCoordinates coordinates, 
                                             String chapterTitle, String rawText, String contentHash) {
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setBookId(bookId);
        chapter.setUniverseId(universeId);
        chapter.setSeriesId(seriesId);
        chapter.setCoordinates(coordinates);
        chapter.setChapterTitle(chapterTitle);
        chapter.setRawText(rawText);
        chapter.setContentHash(contentHash);
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(chapter.getCreatedAt());
        return chapter;
    }

    /**
     * Create a chapter for a standalone book (no series).
     */
    public static Chapter createStandalone(UUID bookId, UUID universeId,
                                         PublicationCoordinates coordinates,
                                         String chapterTitle, String rawText, String contentHash) {
        return createWithReferences(bookId, universeId, null, coordinates, 
                                  chapterTitle, rawText, contentHash);
    }
}
