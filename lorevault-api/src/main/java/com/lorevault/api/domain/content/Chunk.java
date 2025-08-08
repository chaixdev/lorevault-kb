package com.lorevault.api.domain.content;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A technical subdivision of a Chapter. Chunks are the most granular level of the hierarchy
 * and are sized for optimal performance in the RAG process. Each chunk will have a vector
 * embedding for semantic retrieval.
 * 
 * For v0.2.0: Direct Chapter → Chunk relationship with deterministic segmentation
 * For v0.3.0+: Will be updated to Scene → Chunk when semantic scene detection is added
 */
@Entity
@Table(
    name = "chunks",
    indexes = {
        @Index(name = "idx_chunks_chapter", columnList = "chapterId"),
        @Index(name = "idx_chunks_scene", columnList = "sceneId"),
        @Index(name = "idx_chunks_position", columnList = "chapterId, chunkNumberInChapter"),
        @Index(name = "idx_chunks_content_hash", columnList = "contentHash")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Foreign key referencing the parent Chapter (aggregate root)
     * Always present - chapter owns all chunks
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    @NotNull
    private Chapter chapter;

    /**
     * Foreign key referencing the parent Scene (v0.3.0+)
     * Optional during transition period - new chunks will use scenes, legacy chunks may not
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private Scene scene;

    /**
     * The sequential order of the chunk within the chapter (1-based)
     */
    @Column(nullable = false)
    @NotNull
    private Integer chunkNumberInChapter;

    /**
     * The absolute start position in the chapter's rawText (0-based)
     */
    @Column(nullable = false)
    @NotNull
    private Integer startCharInChapter;

    /**
     * The absolute end position in the chapter's rawText (exclusive, 0-based)
     */
    @Column(nullable = false)
    @NotNull
    private Integer endCharInChapter;

    /**
     * SHA-256 hash of the chunk content for deduplication and change detection
     */
    @Column(nullable = false)
    @NotNull
    private String contentHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get the character length of this chunk
     */
    public int getLength() {
        return endCharInChapter - startCharInChapter;
    }
}
