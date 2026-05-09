package com.lorevault.api.web.command.ingestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for the prepare endpoint.
 *
 * <p>Creates a chapter and an ingestion job without triggering the
 * async pipeline. The caller drives individual steps via the
 * step execution endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrepareChapterRequest {

    @NotNull
    private UUID bookId;

    @NotNull
    private Integer chapterNumber;

    @NotBlank
    @Size(max = 500)
    private String chapterTitle;

    @NotBlank
    @Size(max = 500_000)
    private String chapterText;
}