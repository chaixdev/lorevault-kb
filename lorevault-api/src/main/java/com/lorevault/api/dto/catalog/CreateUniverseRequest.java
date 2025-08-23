package com.lorevault.api.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new Universe in the catalog
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUniverseRequest {
    
    @NotBlank(message = "Universe name is required")
    @Size(max = 255, message = "Universe name must not exceed 255 characters")
    private String name;
}