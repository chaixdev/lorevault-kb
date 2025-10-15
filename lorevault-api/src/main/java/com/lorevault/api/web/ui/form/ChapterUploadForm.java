package com.lorevault.api.web.ui.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class ChapterUploadForm {

    private UUID universeId;

    @NotNull(message = "Please select a book")
    private UUID bookId;

    @NotNull(message = "Chapter number is required")
    @Min(value = 1, message = "Chapter number must be at least 1")
    private Integer chapterNumber;

    @Size(max = 255, message = "Chapter title must not exceed 255 characters")
    private String chapterTitle;

    @NotNull(message = "Please choose a file to upload")
    private MultipartFile file;
}
