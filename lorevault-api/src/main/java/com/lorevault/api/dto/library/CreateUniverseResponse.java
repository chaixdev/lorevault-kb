package com.lorevault.api.dto.library;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for universe creation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUniverseResponse {
    
    private UUID universeId;
    private String name;
    private String slug;
    private boolean created; // true if newly created, false if already existed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static CreateUniverseResponse newlyCreated(UUID id, String name, String slug, 
                                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CreateUniverseResponse(id, name, slug, true, createdAt, updatedAt);
    }
    
    public static CreateUniverseResponse existing(UUID id, String name, String slug, 
                                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CreateUniverseResponse(id, name, slug, false, createdAt, updatedAt);
    }
}
