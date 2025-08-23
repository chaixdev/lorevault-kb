package com.lorevault.api.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a new Series in the catalog
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSeriesRequest {
    
    @NotNull(message = "Universe ID is required")
    private UUID universeId;
    
    @NotBlank(message = "Series name is required")
    @Size(max = 255, message = "Series name must not exceed 255 characters")
    private String name;
}