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
@Node("LlmCallResponse")
public class LlmCallResponse {
    @Id
    private UUID id;

    private String body;
}
