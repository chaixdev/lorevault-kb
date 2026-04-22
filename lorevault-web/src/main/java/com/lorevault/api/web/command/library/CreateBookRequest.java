package com.lorevault.api.web.command.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a new Book in the library
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookRequest {
    
    @NotNull(message = "Universe ID is required")
    private UUID universeId;
    
    // Optional - null for standalone books
    private UUID seriesId;
    
    @NotBlank(message = "Book title is required")
    @Size(max = 255, message = "Book title must not exceed 255 characters")
    private String title;
    
    // Optional - null for standalone books, required for series books
    private Integer bookNumber;
    
    /**
     * Factory methods for common use cases
     */
    public static CreateBookRequest standalone(UUID universeId, String title) {
        return new CreateBookRequest(universeId, null, title, null);
    }
    
    public static CreateBookRequest inSeries(UUID universeId, UUID seriesId, String title, Integer bookNumber) {
        return new CreateBookRequest(universeId, seriesId, title, bookNumber);
    }
}
