package com.lorevault.api.dto.library;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for book creation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookResponse {
    
    private UUID bookId;
    private UUID universeId;
    private String universeName;
    private UUID seriesId;
    private String seriesName;
    private String title;
    private Integer bookNumber;
    private boolean created; // true if newly created, false if already existed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static CreateBookResponse newlyCreated(UUID bookId, UUID universeId, String universeName,
                                                 UUID seriesId, String seriesName, String title, 
                                                 Integer bookNumber, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CreateBookResponse(bookId, universeId, universeName, seriesId, seriesName, title, bookNumber, true, createdAt, updatedAt);
    }
    
    public static CreateBookResponse existing(UUID bookId, UUID universeId, String universeName,
                                             UUID seriesId, String seriesName, String title, 
                                             Integer bookNumber, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CreateBookResponse(bookId, universeId, universeName, seriesId, seriesName, title, bookNumber, false, createdAt, updatedAt);
    }
}
