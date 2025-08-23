package com.lorevault.api.infrastructure.persistence.neo4j.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Neo4j node representation of a Book
 */
@Node("Book")
@Data
@NoArgsConstructor
public class BookNode {
    
    @Id
    private UUID id;
    
    private UUID universeId;
    private UUID seriesId; // nullable for standalone books
    private String universe;
    private String series;
    private Integer bookNumber;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Relationship(type = "IN_UNIVERSE", direction = Relationship.Direction.OUTGOING)
    private UniverseNode universeNode;
    
    @Relationship(type = "IN_SERIES", direction = Relationship.Direction.OUTGOING)
    private SeriesNode seriesNode;
    
    public BookNode(UUID id, UUID universeId, UUID seriesId, String universe, String series,
                    Integer bookNumber, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.universeId = universeId;
        this.seriesId = seriesId;
        this.universe = universe;
        this.series = series;
        this.bookNumber = bookNumber;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}