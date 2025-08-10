package com.lorevault.api.test;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory test vector store with deterministic embedding for integration tests.
 * Uses hash-based vectors for predictable similarity ordering.
 */
public class TestVectorStore implements VectorStore {

    private static final int DIM = 1536;
    private final Map<String, DocumentWithVector> store = new ConcurrentHashMap<>();

    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null) text = "";
            float[] vec = buildVector(text);
            String id = generateId(doc);
            store.put(id, new DocumentWithVector(doc, vec));
        }
    }

    @Override
    public void delete(List<String> idList) {
        for (String id : idList) {
            store.remove(id);
        }
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        // Ignore filter for test simplicity
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.builder().query(query).topK(5).build());
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (store.isEmpty()) {
            return Collections.emptyList();
        }

        float[] queryVec = buildVector(request.getQuery());
        int topK = Math.max(1, request.getTopK());

        List<ScoredDocument> scored = new ArrayList<>();
        for (DocumentWithVector dwv : store.values()) {
            double score = cosineSimilarity(queryVec, dwv.vector);
            if (score >= request.getSimilarityThreshold()) {
                String text = dwv.document.getText();
                if (text == null) text = "";
                Document docWithScore = new Document(text, dwv.document.getMetadata());
                // Set score on document metadata for retrieval
                Map<String, Object> metadata = new HashMap<>(docWithScore.getMetadata());
                metadata.put("score", score);
                String finalText = docWithScore.getText();
                if (finalText == null) finalText = "";
                Document scoredDoc = new Document(finalText, metadata);
                scored.add(new ScoredDocument(scoredDoc, score));
            }
        }

        return scored.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score)) // descending
                .limit(topK)
                .map(sd -> sd.document)
                .collect(Collectors.toList());
    }

    public List<Document> similaritySearch(SearchRequest request, Filter.Expression filterExpression) {
        // Ignore filter for test simplicity
        return similaritySearch(request);
    }

    private float[] buildVector(String input) {
        float[] vec = new float[DIM];
        if (input != null) {
            String[] toks = input.toLowerCase().split("[^a-z0-9]+");
            for (String t : toks) {
                if (t.isEmpty()) continue;
                int pos = (t.hashCode() & 0x7fffffff) % DIM;
                vec[pos] += 1f;
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

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot; // already normalized
    }

    private String generateId(Document doc) {
        // Use metadata or content hash as ID
        Map<String, Object> meta = doc.getMetadata();
        if (meta != null && meta.containsKey("chunkId")) {
            return meta.get("chunkId").toString();
        }
        String text = doc.getText();
        if (text == null) text = "";
        return "doc_" + Math.abs(text.hashCode());
    }

    private static class DocumentWithVector {
        final Document document;
        final float[] vector;

        DocumentWithVector(Document document, float[] vector) {
            this.document = document;
            this.vector = vector;
        }
    }

    private static class ScoredDocument {
        final Document document;
        final double score;

        ScoredDocument(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}
