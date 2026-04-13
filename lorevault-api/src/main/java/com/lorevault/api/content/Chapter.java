package com.lorevault.api.content;

import com.lorevault.api.support.PublicationCoordinates;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

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
@Node("Chapter")
public class Chapter {
    @Id
    private UUID id;

    /**
     * Stable UUID references for graph relationships
     */
    private UUID bookId;
    private UUID universeId;  // denormalized for fast filtering
    private UUID seriesId;    // denormalized for fast filtering, nullable for standalone books

    private String universe;
    private String series;
    private String bookTitle;
    private Integer bookNumber;
    private Integer chapterNumber;

    /**
     * Embedded coordinates object defining the chapter's position in the published text corpus.
     * Essential for spoiler gating (ordering) and human-readable display.
     * Transient: individual fields (universe, series, bookTitle, etc.) are persisted instead.
     */
    @org.springframework.data.annotation.Transient
    private PublicationCoordinates coordinates;

    /**
     * The title of the chapter
     */
    private String chapterTitle;

    /**
     * The full, unmodified chapter text
     */
    @Property("rawText")
    private String rawText;

    /**
     * A SHA-256 hash of rawText for deduplication
     */
    private String contentHash;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Relationship(type = "IN_BOOK", direction = Relationship.Direction.OUTGOING)
    private Book book;

    /**
     * Scenes within this chapter, ordered by scene index
     */
    @Relationship(type = "HAS_SCENE")
    private List<Scene> scenes = new ArrayList<>();

    /**
     * All chunks within this chapter
     */
    @Relationship(type = "HAS_CHUNK")
    private List<Chunk> chunks = new ArrayList<>();

    // =====================================
    // Aggregate Root Methods for Scenes
    // =====================================

    /**
     * Get read-only view of scenes
     */
    public List<Scene> getScenes() {
        if (scenes == null) {
            scenes = new ArrayList<>();
        }
        return scenes;
    }

    // Neo4j SDN passes null when no HAS_SCENE relationships exist
    public void setScenes(List<Scene> scenes) {
        this.scenes = scenes == null ? new ArrayList<>() : scenes;
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
     * Add an existing scene to this chapter (for triad analysis and other temporary operations)
     */
    public void addExistingScene(Scene scene) {
        if (scene != null) {
            scene.setChapter(this);
            scenes.add(scene);
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
        if (chunks == null) {
            chunks = new ArrayList<>();
        }
        return chunks;
    }

    // Neo4j SDN passes null when no HAS_CHUNK relationships exist
    public void setChunks(List<Chunk> chunks) {
        this.chunks = chunks == null ? new ArrayList<>() : chunks;
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
        if (universe != null) return universe;
        return coordinates != null ? coordinates.getUniverse() : null;
    }

    /**
     * Get the series name from coordinates
     */
    public String getSeries() {
        if (series != null) return series;
        return coordinates != null ? coordinates.getSeries() : null;
    }

    /**
     * Get the book number from coordinates
     */
    public Integer getBookNumber() {
        if (bookNumber != null) return bookNumber;
        return coordinates != null ? coordinates.getBookNumber() : null;
    }

    /**
     * Get the chapter number from coordinates
     */
    public Integer getChapterNumber() {
        if (chapterNumber != null) return chapterNumber;
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
        chapter.id = UUID.randomUUID();
        chapter.bookId = bookId;
        chapter.universeId = universeId;
        chapter.seriesId = seriesId;
        chapter.coordinates = coordinates;
        if (coordinates != null) {
            chapter.universe = coordinates.getUniverse();
            chapter.series = coordinates.getSeries();
            chapter.bookTitle = coordinates.getBookTitle();
            chapter.bookNumber = coordinates.getBookNumber();
            chapter.chapterNumber = coordinates.getChapterNumber();
        }
        chapter.chapterTitle = chapterTitle;
        chapter.rawText = rawText;
        chapter.contentHash = contentHash;
        LocalDateTime now = LocalDateTime.now();
        chapter.createdAt = now;
        chapter.updatedAt = now;
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
