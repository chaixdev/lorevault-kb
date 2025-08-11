package com.lorevault.api.domain.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value object defining the position of a chapter within the publication hierarchy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationCoordinates {

    /**
     * The top-level fictional universe or franchise (e.g., "Middle Earth", "Star Wars")
     */
    @NotBlank
    private String universe;

    /**
     * The series or collection within the universe (e.g., "The Lord of the Rings", "Original Trilogy")
     * Null for standalone books within a universe
     */
    private String series;

    /**
     * The sequential order of the book in the series (1-based indexing)
     */
    @NotNull
    private Integer bookNumber;

    /**
     * The part number within the book (1-based indexing, null if book has no parts)
     */
    private Integer partNumber;

    /**
     * The chapter number within the book/part (1-based indexing)
     */
    @NotNull
    private Integer chapterNumber;
}
