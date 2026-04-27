package com.lorevault.api.ingestion.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Data
@NoArgsConstructor
@Node("LlmCallResponse")
public class LlmCallResponse {
    @Id
    private UUID id;

    private String body;
    private String bodyHash;
    private Boolean truncated;

    public LlmCallResponse(UUID id, String body, String bodyHash, Boolean truncated) {
        this.id = id;
        this.body = body;
        this.bodyHash = bodyHash;
        this.truncated = truncated;
    }

    public LlmCallResponse(UUID id, String body) {
        this(id, body, null, null);
    }
}
