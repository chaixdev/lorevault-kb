package com.lorevault.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.config.EmbeddingProperties;
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
        EmbeddingProperties props = new EmbeddingProperties();
        props.setDim(5);
        RestTemplate mockRest = mock(RestTemplate.class);
        EmbeddingModelAdapter adapter = new EmbeddingModelAdapter(props, mockRest);

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

        // Inject @Value fields
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "modelId", "gemini-embedding-001");
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "baseUrl", "https://example.com/v1beta/openai");
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "apiKey", "DUMMY");

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
