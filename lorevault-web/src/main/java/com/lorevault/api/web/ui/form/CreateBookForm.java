package com.lorevault.api.web.ui.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateBookForm {

    @NotNull(message = "Please select a universe")
    private UUID universeId;

    private UUID seriesId;

    @NotBlank(message = "Book title is required")
    @Size(max = 255, message = "Book title must not exceed 255 characters")
    private String title;

    @Min(value = 1, message = "Book number must be at least 1")
    private Integer bookNumber;
}
