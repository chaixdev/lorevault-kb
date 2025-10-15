package com.lorevault.api.web.ui.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUniverseForm {

    @NotBlank(message = "Universe name is required")
    @Size(max = 255, message = "Universe name must not exceed 255 characters")
    private String name;
}
