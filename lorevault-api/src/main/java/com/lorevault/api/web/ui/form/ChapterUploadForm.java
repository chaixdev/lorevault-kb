package com.lorevault.api.web.ui.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ChapterUploadForm {

    private String universeSelection;

    private String seriesSelection;

    private String bookSelection;

    @Size(max = 255, message = "Universe name must not exceed 255 characters")
    private String newUniverseName;

    @Size(max = 255, message = "Series name must not exceed 255 characters")
    private String newSeriesName;

    @Size(max = 255, message = "Book title must not exceed 255 characters")
    private String newBookTitle;

    @Min(value = 1, message = "Book number must be at least 1")
    private Integer newBookNumber;

    @NotNull(message = "Chapter number is required")
    @Min(value = 1, message = "Chapter number must be at least 1")
    private Integer chapterNumber;

    @Size(max = 255, message = "Chapter title must not exceed 255 characters")
    private String chapterTitle;

    @NotNull(message = "Please choose a file to upload")
    private MultipartFile file;
}
