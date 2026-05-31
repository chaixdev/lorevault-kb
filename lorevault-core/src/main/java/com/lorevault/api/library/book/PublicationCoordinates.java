package com.lorevault.api.library.book;

import static com.lorevault.api.common.StringSanitizer.toSnakeCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value object defining the position of a chapter within the publication
 * hierarchy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationCoordinates {

    @NotBlank
    private String universe;
    @NotBlank
    private String series;
    @NotBlank
    private String bookTitle;
    @NotBlank
    private String chapterTitle;

    @NotNull
    private Integer bookNumber;
    @NotNull
    private Integer chapterNumber;

    public String getPublicationId() {
        String universeSlug = toSnakeCase(universe);
        String seriesSlug = toSnakeCase(series);

        return String.format(
                "%s/%s/%03d/%05d",
                universeSlug,
                seriesSlug,
                bookNumber,
                chapterNumber);
    }

}