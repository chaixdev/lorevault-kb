package com.lorevault.api.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embeddable component that defines the precise position of a chapter within
 * the published text corpus. Used for reading order, spoiler prevention,
 * and organizing content by publication sequence.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PublicationReference {

    /**
     * The top-level fictional universe or franchise (e.g., "Middle Earth", "Star Wars")
     */
    @NotBlank
    private String universe;

    /**
     * The series or collection within the universe (e.g., "The Lord of the Rings", "Original Trilogy")
     */
    @NotBlank
    private String series;

    /**
     * The order of the book in the series (1-based indexing)
     */
    @NotNull
    private Integer bookNumber;

    /**
     * The part number within the book (1-based, null if book has no parts)
     */
    private Integer partNumber;

    /**
     * The chapter number within the book/part (1-based indexing)
     */
    @NotNull
    private Integer chapterNumber;
}
