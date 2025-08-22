package com.lorevault.api.infrastructure.persistence.neo4j.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

@Node("LlmCallRecord")
public class LlmCallRecordNode {
    @Id
    private UUID id;

    // Linkage
    private UUID jobId;
    private UUID statusRecordId;

    // Classification
    private String step;

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
    private String promptTemplateId;
    private Boolean storeRenderedPrompt;
    private String renderedPrompt;

    // Payloads
    private String inputPreview;
    private String responseBody;
    private String responseHash;
    private Boolean truncated;

    @CreatedDate
    private LocalDateTime createdAt;

    // Optional convenience relationships (not required for queries but useful visually)
    @Relationship(type = "OF_JOB", direction = Relationship.Direction.OUTGOING)
    private IngestionJobNode job;

    @Relationship(type = "OF_STATUS", direction = Relationship.Direction.OUTGOING)
    private StatusRecordNode status;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public UUID getStatusRecordId() { return statusRecordId; }
    public void setStatusRecordId(UUID statusRecordId) { this.statusRecordId = statusRecordId; }
    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Boolean getTokensEstimated() { return tokensEstimated; }
    public void setTokensEstimated(Boolean tokensEstimated) { this.tokensEstimated = tokensEstimated; }
    public String getPromptTemplateId() { return promptTemplateId; }
    public void setPromptTemplateId(String promptTemplateId) { this.promptTemplateId = promptTemplateId; }
    public Boolean getStoreRenderedPrompt() { return storeRenderedPrompt; }
    public void setStoreRenderedPrompt(Boolean storeRenderedPrompt) { this.storeRenderedPrompt = storeRenderedPrompt; }
    public String getRenderedPrompt() { return renderedPrompt; }
    public void setRenderedPrompt(String renderedPrompt) { this.renderedPrompt = renderedPrompt; }
    public String getInputPreview() { return inputPreview; }
    public void setInputPreview(String inputPreview) { this.inputPreview = inputPreview; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public String getResponseHash() { return responseHash; }
    public void setResponseHash(String responseHash) { this.responseHash = responseHash; }
    public Boolean getTruncated() { return truncated; }
    public void setTruncated(Boolean truncated) { this.truncated = truncated; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public IngestionJobNode getJob() { return job; }
    public void setJob(IngestionJobNode job) { this.job = job; }
    public StatusRecordNode getStatus() { return status; }
    public void setStatus(StatusRecordNode status) { this.status = status; }
}
