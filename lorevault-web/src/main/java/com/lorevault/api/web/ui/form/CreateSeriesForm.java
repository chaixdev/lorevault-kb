package com.lorevault.api.web.ui.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSeriesForm {

    @NotNull(message = "Please select a universe")
    private UUID universeId;

    @NotBlank(message = "Series name is required")
    @Size(max = 255, message = "Series name must not exceed 255 characters")
    private String name;
}
