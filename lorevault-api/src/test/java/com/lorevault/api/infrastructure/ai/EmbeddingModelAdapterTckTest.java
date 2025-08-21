package com.lorevault.api.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.configuration.properties.LoreVaultEmbeddingProperties;
import com.lorevault.api.configuration.properties.LoreVaultModelsProperties;
import com.lorevault.api.tck.ai.EmbeddingPortTCK;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TCK for EmbeddingModelAdapter using mocked RestTemplate.
 */
@ExtendWith(MockitoExtension.class)
public class EmbeddingModelAdapterTckTest extends EmbeddingPortTCK {

    @Mock private RestTemplate mockRestTemplate;
    private EmbeddingModelAdapter adapter;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Create minimal properties using null values to trigger defaults
        var embeddingProps = new LoreVaultEmbeddingProperties(
            new LoreVaultEmbeddingProperties.ModelProperties("openai", "text-embedding-ada-002", 1536, 32),
            new LoreVaultEmbeddingProperties.ProcessingProperties(true, 5, 30000L, 3, 200L, 2.0, 2000L)
        );
        var modelProps = new LoreVaultModelsProperties(
            new LoreVaultModelsProperties.ModelProperties("openai-compatible", "https://api.openai.com/v1", "/embeddings", "fake-key", "text-embedding-ada-002", 0.3, 1.0, 2048),
            null, null
        );
        
        adapter = new EmbeddingModelAdapter(embeddingProps, modelProps, mockRestTemplate);
        
        // Mock successful embedding response
        mockSuccessfulEmbeddingResponse();
    }

    @Override
    protected EmbeddingPort createPort() {
        return adapter;
    }

    private void mockSuccessfulEmbeddingResponse() {
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
            .thenAnswer(invocation -> {
                // Extract the request body from the HttpEntity
                HttpEntity<?> httpEntity = (HttpEntity<?>) invocation.getArgument(1);
                JsonNode requestBody = (JsonNode) httpEntity.getBody();
                if (requestBody == null) {
                    throw new RuntimeException("No request body found in HttpEntity");
                }
                JsonNode inputNode = requestBody.get("input");
                int inputCount = inputNode.isArray() ? inputNode.size() : 1;
                
                // Create response with matching number of embeddings
                ObjectNode response = objectMapper.createObjectNode();
                ArrayNode data = objectMapper.createArrayNode();
                
                for (int vectorIndex = 0; vectorIndex < inputCount; vectorIndex++) {
                    ObjectNode item = objectMapper.createObjectNode();
                    ArrayNode embedding = objectMapper.createArrayNode();
                    
                    // Create unique 1536-dimensional vector for each input
                    for (int i = 0; i < 1536; i++) {
                        embedding.add(0.1 + (i * 0.001) + (vectorIndex * 0.0001)); // Slightly different per vector
                    }
                    item.set("embedding", embedding);
                    data.add(item);
                }
                
                response.set("data", data);
                return new ResponseEntity<>(response, HttpStatus.OK);
            });
    }
}
