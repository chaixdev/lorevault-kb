package com.lorevault.api.graph.timeline.domain;

import com.lorevault.api.graph.event.scene.Scene;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@RelationshipProperties
public class TemporalEdge {

    @Id
    @GeneratedValue
    private String id;

    private TemporalRelation temporalRelation;
    private CertaintyLevel certainty;
    private double weight;
    private String source;
    private String rationale;
    private Long evidenceStartOffset;
    private Long evidenceEndOffset;
    private UUID evidenceChunkId;

    @TargetNode
    private Scene later;

}
