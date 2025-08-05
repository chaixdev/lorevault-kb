package com.lorevault.api.dto;

import com.lorevault.api.model.PublicationCoordinates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting a chapter for ingestion
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitChapterRequest {

    /**
     * Coordinates defining the chapter's position in the published text corpus
     */
    @Valid
    @NotNull
    private PublicationCoordinates coordinates;

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
