package com.lorevault.api.model;

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
import java.util.UUID;

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
}
