package com.lorevault.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.configuration.properties.LoreVaultEmbeddingProperties;
import com.lorevault.api.configuration.properties.LoreVaultModelsProperties;
import com.lorevault.api.infrastructure.ai.EmbeddingModelAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Adapter test without real network call. Mocks RestTemplate and verifies parsing.
 */
class EmbeddingModelAdapterTest {

    @Test
    void embedBatch_parsesVectorsFromMockResponse() throws Exception {
        var modelProps = new LoreVaultEmbeddingProperties.ModelProperties("openai", "test", 5, 32);
        var processingProps = new LoreVaultEmbeddingProperties.ProcessingProperties(true, 5, 30000L, 3, 200L, 2.0, 2000L);
        var props = new LoreVaultEmbeddingProperties(modelProps, processingProps);
        
        // Create mock models properties for the new constructor parameter
        var embeddingModel = new LoreVaultModelsProperties.ModelProperties("openai-compatible", "https://example.com/v1beta/openai", "/embeddings", "DUMMY", "gemini-embedding-001", 0.3, 1.0, 512);
        var nlpSmall = new LoreVaultModelsProperties.ModelProperties("openai-compatible", "http://test", "/chat/completions", "test-key", "test-model", 0.3, 1.0, 2048);
        var nlpBig = new LoreVaultModelsProperties.ModelProperties("openai-compatible", "http://test", "/chat/completions", "test-key", "test-model", 0.2, 1.0, 4096);
        var modelsProps = new LoreVaultModelsProperties(embeddingModel, nlpSmall, nlpBig);
        
        RestTemplate mockRest = mock(RestTemplate.class);
        EmbeddingModelAdapter adapter = new EmbeddingModelAdapter(props, modelsProps, mockRest);

        String json = "{\n" +
                "  \"data\": [\n" +
                "    { \"embedding\": [0.11,0.22,0.33,0.44,0.55] },\n" +
                "    { \"embedding\": [0.66,0.77,0.88,0.99,1.11] }\n" +
                "  ]\n" +
                "}";
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        when(mockRest.postForEntity(anyString(), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(ResponseEntity.ok(node));

        List<double[]> result = adapter.embedBatch(List.of("a","b"));
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(0.11,0.22,0.33,0.44,0.55);
        assertThat(result.get(1)).containsExactly(0.66,0.77,0.88,0.99,1.11);

        // Verify headers set (content type) via captured entity
        verify(mockRest, times(1)).postForEntity(anyString(), argThat(arg -> {
            if (!(arg instanceof HttpEntity<?> entity)) return false;
            HttpHeaders h = entity.getHeaders();
            return MediaType.APPLICATION_JSON.equals(h.getContentType()) && h.getFirst(HttpHeaders.AUTHORIZATION) != null;
        }), eq(com.fasterxml.jackson.databind.JsonNode.class));
    }
}
