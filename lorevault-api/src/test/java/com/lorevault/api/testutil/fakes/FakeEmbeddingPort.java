package com.lorevault.api.testutil.fakes;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

public class FakeEmbeddingPort implements EmbeddingModel {

    private final String modelId;
    private final int dimension;

    public FakeEmbeddingPort() {
        this("fake-embedding-1", 128);
    }

    public FakeEmbeddingPort(String modelId, int dimension) {
        this.modelId = modelId;
        this.dimension = dimension;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            float[] vector = embed(instructions.get(i));
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            return new float[0];
        }
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return deterministicVector(text, dimension);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embed(text));
        }
        return out;
    }

    public String getModelId() {
        return modelId;
    }

    public int getDimension() {
        return dimension;
    }

    private static float[] deterministicVector(String text, int dim) {
        int seed = (text == null ? 0 : text.hashCode());
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            double val = Math.sin(seed + i * 0.13) * Math.cos(seed * 0.17 + i);
            v[i] = (float) val;
        }
        return v;
    }
}
