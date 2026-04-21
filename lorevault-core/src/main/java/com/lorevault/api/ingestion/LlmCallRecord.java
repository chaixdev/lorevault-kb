package com.lorevault.api.ingestion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain model capturing a single LLM call made during an ingestion job step.
 * Records request/response payload previews and telemetry for observability/tracing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("LlmCallRecord")
public class LlmCallRecord {
    @Id
    private UUID id;

    // Linkage
    private UUID jobId;
    private UUID statusRecordId; // optional: current status at call time

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
    private Boolean storeRenderedPrompt; // whether rendered prompt is stored
    private String renderedPrompt; // optional, may be omitted

    // Payloads (capped per configuration)
    private String inputPreview; // first N chars of input
    private String responseBody; // full/capped response
    private String responseHash; // SHA-256 of response for integrity when truncated
    private Boolean truncated; // whether response was truncated to max size

    @CreatedDate
    private LocalDateTime createdAt; // set by persistence layer

    // Optional convenience relationships (not required for queries but useful visually)
    @Relationship(type = "OF_JOB", direction = Relationship.Direction.OUTGOING)
    private IngestionJob job;

    @Relationship(type = "OF_STATUS", direction = Relationship.Direction.OUTGOING)
    private StatusRecord status;
}
