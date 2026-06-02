package com.lorevault.api.graph.event.scene;

import com.lorevault.api.library.chunk.Chunk;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Relationship properties between Scene and Chunk.
 * Carries ordering/indexing information so Chunk remains context-agnostic.
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RelationshipProperties
public class SceneHasChunk {

    @Id
    @GeneratedValue
    private String id;

    private Integer chunkIndex; // sequential index within the scene

    @TargetNode
    private Chunk chunk;
}
