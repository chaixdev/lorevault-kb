package com.lorevault.api.dto.ingestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for submitting a chapter for ingestion
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitChapterRequest {

    /**
     * Target book identifier for this chapter submission
     */
    @NotNull
    private UUID bookId;

    /**
     * The number of the chapter within the book (1-based)
     */
    @NotNull
    private Integer chapterNumber;

    /**
     * The title of the chapter
     */
    @NotBlank
    private String chapterTitle;

    /**
     * The full chapter text to be processed
     */
    @NotBlank
    private String chapterText;
}
