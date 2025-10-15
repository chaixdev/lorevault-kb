package com.lorevault.api.web.ui.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Combined form for creating universe, series (optional), and book in one submission.
 * Supports three workflows:
 * 1. Create new universe + new book (standalone)
 * 2. Create new universe + new series + new book
 * 3. Use existing universe/series + create new book
 */
@Data
public class CreateLibraryForm {

    // Universe fields
    private UUID existingUniverseId;
    
    @Size(max = 255, message = "Universe name must not exceed 255 characters")
    private String newUniverseName;

    // Series fields (optional)
    private UUID existingSeriesId;
    
    @Size(max = 255, message = "Series name must not exceed 255 characters")
    private String newSeriesName;

    // Book fields (required)
    @NotBlank(message = "Book title is required")
    @Size(max = 255, message = "Book title must not exceed 255 characters")
    private String bookTitle;

    @Min(value = 1, message = "Book number must be at least 1")
    private Integer bookNumber;

    /**
     * Determines if we're creating a new universe or using an existing one
     */
    public boolean isCreatingNewUniverse() {
        return existingUniverseId == null && newUniverseName != null && !newUniverseName.isBlank();
    }

    /**
     * Determines if we're creating a new series
     */
    public boolean isCreatingNewSeries() {
        return existingSeriesId == null && newSeriesName != null && !newSeriesName.isBlank();
    }

    /**
     * Validates that either a universe is selected or a new one is being created
     */
    public boolean hasValidUniverse() {
        return existingUniverseId != null || isCreatingNewUniverse();
    }
}
