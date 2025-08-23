package com.lorevault.api.dto.library;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for series creation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSeriesResponse {
    
    private UUID seriesId;
    private UUID universeId;
    private String universeName;
    private String name;
    private boolean created; // true if newly created, false if already existed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static CreateSeriesResponse newlyCreated(UUID seriesId, UUID universeId, String universeName, 
                                                   String name, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CreateSeriesResponse(seriesId, universeId, universeName, name, true, createdAt, updatedAt);
    }
    
    public static CreateSeriesResponse existing(UUID seriesId, UUID universeId, String universeName, 
                                               String name, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CreateSeriesResponse(seriesId, universeId, universeName, name, false, createdAt, updatedAt);
    }
}
