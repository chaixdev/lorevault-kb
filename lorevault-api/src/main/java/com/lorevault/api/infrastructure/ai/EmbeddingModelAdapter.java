package com.lorevault.api.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.config.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding adapter invoking Gemini embedding model via OpenAI-compatible endpoint.
 * Falls back to empty vectors on failure (service will skip persistence for zero-length vectors).
 */
@Component
@Slf4j
public class EmbeddingModelAdapter implements EmbeddingPort {

    private final EmbeddingProperties embeddingProperties;
    private final RestTemplate restTemplate; // injected for testability
    private ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.embedding.options.model:gemini-embedding-001}")
    private String modelId;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl; // e.g. https://generativelanguage.googleapis.com/v1beta/openai

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public EmbeddingModelAdapter(EmbeddingProperties embeddingProperties, RestTemplate restTemplate) {
        this.embeddingProperties = embeddingProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public double[] embed(String text) {
        if (text == null) return new double[0];
        List<double[]> list = embedBatch(List.of(text));
        return list.isEmpty() ? new double[0] : list.get(0);
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        try {
            long start = System.currentTimeMillis();
            String url = normalizeBase(baseUrl) + "/embeddings";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            // OpenAI compatible body uses "input"
            var body = objectMapper.createObjectNode();
            body.put("model", modelId);
            body.set("input", objectMapper.valueToTree(texts));
            ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode root = resp.getBody();
            List<double[]> vectors = new ArrayList<>();
            if (root != null && root.has("data")) {
                for (JsonNode datum : root.get("data")) {
                    JsonNode emb = datum.get("embedding");
                    if (emb == null || !emb.isArray()) {
                        vectors.add(new double[0]);
                        continue;
                    }
                    double[] vec = new double[emb.size()];
                    for (int i = 0; i < emb.size(); i++) vec[i] = emb.get(i).asDouble();
                    vectors.add(vec);
                }
            }
            long ms = System.currentTimeMillis() - start;
            log.debug("[Embeddings] remote call model={} batchSize={} ms={} firstVecDim={}", modelId, texts.size(), ms, vectors.isEmpty() ? 0 : vectors.get(0).length);
            // Size reconciliation
            if (vectors.size() != texts.size()) {
                log.warn("[Embeddings] Mismatch textCount={} vectorCount={} model={}", texts.size(), vectors.size(), modelId);
            }
            return vectors;
        } catch (Exception e) {
            log.error("[Embeddings] Remote embedding call failed model={} size={} error={}", modelId, texts.size(), e.getMessage());
            return texts.stream().map(t -> new double[0]).toList();
        }
    }

    private String normalizeBase(String b) {
        if (b.endsWith("/")) return b.substring(0, b.length() - 1);
        return b;
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public int getDimension() {
        return embeddingProperties.getDim();
    }
}
