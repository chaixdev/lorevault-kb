package com.lorevault.api.content.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a series within a universe (e.g., "Stormlight Archive" within "Cosmere").
 * Provides stable UUID for graph relationships and metadata for display.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("Series")
public class Series {
    @Id
    private UUID id;
    
    /**
     * Parent universe reference for stable relationships
     */
    private UUID universeId;
    
    /**
     * Display metadata - kept for human-readable context
     */
    private String universeName; // denormalized for convenience
    private String name;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Factory method to create a series within a universe.
     */
    public static Series create(UUID universeId, String universeName, String seriesName) {
        Series s = new Series();
        s.setId(UUID.randomUUID());
        s.setUniverseId(universeId);
        s.setUniverseName(universeName);
        s.setName(seriesName);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(s.getCreatedAt());
        return s;
    }
}
