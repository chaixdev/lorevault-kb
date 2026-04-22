package com.lorevault.api.content.entities;

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
    private String id;

    private Integer chunkIndex; // sequential index within the scene

    @TargetNode
    private Chunk chunk;
}
