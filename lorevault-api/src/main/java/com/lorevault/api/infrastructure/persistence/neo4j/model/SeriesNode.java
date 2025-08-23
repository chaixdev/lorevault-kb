package com.lorevault.api.infrastructure.persistence.neo4j.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Neo4j node representation of a Series
 */
@Node("Series")
@Data
@NoArgsConstructor
public class SeriesNode {
    
    @Id
    private UUID id;
    
    private UUID universeId;
    private String universeName;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Relationship(type = "IN_UNIVERSE", direction = Relationship.Direction.OUTGOING)
    private UniverseNode universe;
    
    public SeriesNode(UUID id, UUID universeId, String universeName, String name, 
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.universeId = universeId;
        this.universeName = universeName;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}