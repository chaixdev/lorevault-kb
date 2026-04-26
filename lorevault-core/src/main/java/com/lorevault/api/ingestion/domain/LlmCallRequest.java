package com.lorevault.api.ingestion.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("LlmCallRequest")
public class LlmCallRequest {
    @Id
    private UUID id;

    private String renderedPrompt;
    private String inputBody;
}
