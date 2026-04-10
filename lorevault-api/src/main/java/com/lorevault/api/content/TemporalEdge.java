package com.lorevault.api.content;

import com.lorevault.api.content.Scene;
import com.lorevault.api.timeline.CertaintyLevel;
import com.lorevault.api.timeline.TemporalRelation;
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
