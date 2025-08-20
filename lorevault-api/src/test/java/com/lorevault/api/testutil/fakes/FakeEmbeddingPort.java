package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.EmbeddingPort;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Fake embedding port for deterministic test embeddings.
 * Generates consistent vectors based on text content hash for reproducible tests.
 */
public final class FakeEmbeddingPort implements EmbeddingPort {
    
    private final String modelId;
    private final int dimension;
    
    public FakeEmbeddingPort() {
        this("fake-embedding-model", 1536);
    }
    
    public FakeEmbeddingPort(String modelId, int dimension) {
        this.modelId = modelId;
        this.dimension = dimension;
    }
    
    @Override
    public double[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new double[dimension];
        }
        return generateDeterministicVector(text);
    }
    
    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null) {
            return List.of();
        }
        return texts.stream()
                .map(this::embed)
                .toList();
    }
    
    @Override
    public String getModelId() {
        return modelId;
    }
    
    @Override
    public int getDimension() {
        return dimension;
    }
    
    /**
     * Generate a deterministic vector based on text content.
     * Uses text hash to seed vector values for reproducible results.
     */
    private double[] generateDeterministicVector(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(text.getBytes());
            
            double[] vector = new double[dimension];
            
            // Use hash bytes to seed vector values
            for (int i = 0; i < dimension; i++) {
                int hashIndex = i % hash.length;
                // Convert byte to double in range [-1, 1]
                vector[i] = (hash[hashIndex] & 0xFF) / 127.5 - 1.0;
            }
            
            // Normalize vector to unit length
            return normalizeVector(vector);
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    private double[] normalizeVector(double[] vector) {
        double norm = Math.sqrt(IntStream.range(0, vector.length)
                .mapToDouble(i -> vector[i] * vector[i])
                .sum());
        
        if (norm == 0) {
            return vector;
        }
        
        return IntStream.range(0, vector.length)
                .mapToDouble(i -> vector[i] / norm)
                .toArray();
    }
}
