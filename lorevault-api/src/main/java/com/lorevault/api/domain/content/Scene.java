package com.lorevault.api.domain.content;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Represents a semantic scene within a chapter. Scenes are identified by AI analysis
 * based on shifts in time, location, or character focus. They serve as the intermediate
 * level in the hierarchy: Chapter -> Scene -> Chunk.
 * 
 * Scenes contain the exact character coordinates within the chapter text and provide
 * contextual boundaries for the chunking process in v0.3.0+.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scene {
    private UUID id;

    /**
     * Foreign key referencing the parent Chapter (aggregate root)
     */
    private Chapter chapter;

    /**
     * The sequential index of the scene within the chapter (0-based, matching AI output)
     */
    private Integer sceneIndex;

    /**
     * AI-generated summary describing the context/content of this scene
     */
    private String contextSummary;

    /**
     * Zero-indexed character position where this scene starts in the chapter text
     */
    private Long startCharacterOffset;

    /**
     * Zero-indexed character position where this scene ends in the chapter text
     */
    private Long endCharacterOffset;

    /**
     * The actual text content of this scene, materialized for traceability and context
     * This supports the distributed content storage model where scenes store their own
     * text content to enable independent access without requiring chapter materialization.
     */
    private String text;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Chunks that belong to this scene (v0.3.0+)
     */
    private List<Chunk> chunks;

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
}
