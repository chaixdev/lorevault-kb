package com.lorevault.api.ai.telemetry;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@Node("LlmCallRequest")
public class LlmCallRequest {
    @Id
    private UUID id;

    private String renderedPrompt;
    private String inputBody;
    private String inputHash;
    private Boolean inputTruncated;

    public LlmCallRequest(
            UUID id,
            String renderedPrompt,
            String inputBody,
            String inputHash,
            Boolean inputTruncated
    ) {
        this.id = id;
        this.renderedPrompt = renderedPrompt;
        this.inputBody = inputBody;
        this.inputHash = inputHash;
        this.inputTruncated = inputTruncated;
    }

    public LlmCallRequest(UUID id, String renderedPrompt, String inputBody) {
        this(id, renderedPrompt, inputBody, null, null);
    }
}
