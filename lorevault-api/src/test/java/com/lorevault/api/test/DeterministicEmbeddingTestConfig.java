package com.lorevault.api.test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic hash-based embedding client for integration tests so we can
 * exercise pgvector + similarity ordering without external model calls.
 */
@Configuration
@Profile("vector-int")
public class DeterministicEmbeddingTestConfig {

    private static final int DIM = 1536; // matches application properties

    @Bean
    EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<String> inputs = request.getInstructions();
                List<Embedding> results = new ArrayList<>(inputs.size());
                int idx = 0;
                for (String text : inputs) {
                    float[] vec = buildVector(text);
                    results.add(new Embedding(vec, idx++));
                }
                return new EmbeddingResponse(results);
            }

            @Override
            public float[] embed(String text) {
                return buildVector(text);
            }

            @Override
            public float[] embed(Document document) {
                return buildVector(document.getText());
            }

            private float[] buildVector(String input) {
                float[] vec = new float[DIM];
                if (input != null) {
                    String[] toks = input.toLowerCase().split("[^a-z0-9]+");
                    for (String t : toks) {
                        if (t.isEmpty()) continue;
                        int pos = (t.hashCode() & 0x7fffffff) % DIM;
                        vec[pos] += 1f; // bag of words count
                    }
                }
                // L2 normalize
                float sumSq = 0f;
                for (float v : vec) sumSq += v * v;
                if (sumSq > 0f) {
                    float norm = (float) Math.sqrt(sumSq);
                    for (int i = 0; i < vec.length; i++) {
                        if (vec[i] != 0f) vec[i] /= norm;
                    }
                }
                return vec;
            }
        };
    }
}
