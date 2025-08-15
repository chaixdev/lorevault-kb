package com.lorevault.api.infrastructure.persistence.neo4j.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Relationship properties between Scene and Chunk.
 * Carries ordering/indexing information so Chunk remains context-agnostic.
 */
@Data
@RelationshipProperties
public class SceneHasChunk {

    @Id
    @GeneratedValue
    private Long id;

    private Integer chunkIndex; // sequential index within the scene

    @TargetNode
    private ChunkNode chunk;
}
