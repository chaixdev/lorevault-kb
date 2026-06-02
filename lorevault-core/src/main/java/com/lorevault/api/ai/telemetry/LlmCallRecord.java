package com.lorevault.api.ai.telemetry;

import com.lorevault.api.orchestration.job.ChapterIngestionJob;
import com.lorevault.api.orchestration.pipeline.Stage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain model capturing a single LLM call made during an ingestion job step.
 * Request/response payloads are owned by dedicated child nodes.
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@Node("LlmCallRecord")
public class LlmCallRecord {
    @Id
    private UUID id;

    // Linkage
    private UUID jobId;
    private UUID stageId; // optional: current stage at call time

    // Classification
    private String step; // e.g., chapter-segmentation | scene-analysis

    // Provider/model metadata
    private String provider;
    private String model;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;

    // Telemetry
    private Long latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private Boolean tokensEstimated;

    // Prompt metadata
    private String promptTemplateId; // identifier/path of the prompt template
    private Boolean storeRenderedPrompt; // whether rendered prompt is stored in request node

    @CreatedDate
    private LocalDateTime createdAt; // set by persistence layer

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Relationship(type = "WITH_REQUEST", direction = Relationship.Direction.OUTGOING)
    private LlmCallRequest request;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Relationship(type = "WITH_RESPONSE", direction = Relationship.Direction.OUTGOING)
    private LlmCallResponse response;

    // Optional convenience relationships (not required for queries but useful visually)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Relationship(type = "OF_JOB", direction = Relationship.Direction.OUTGOING)
    private ChapterIngestionJob job;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Relationship(type = "OF_STAGE", direction = Relationship.Direction.OUTGOING)
    private Stage stage;
}
