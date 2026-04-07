package com.lorevault.api.infrastructure.persistence.neo4j.model;

import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.timeline.CertaintyLevel;
import com.lorevault.api.domain.timeline.TemporalRelation;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.util.UUID;

@Data
@RelationshipProperties
public class TemporalEdge {

    @Id
    @GeneratedValue
    private Long id;

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
