package com.lorevault.api.service.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkEmbeddingService {

    private final ContentPersistencePort contentPersistencePort; // for chunk retrieval & persistence
    private final EmbeddingPort embeddingPort; // generation port

    @Value("${lorevault.embedding.dim:3072}")
    private int embeddingDim = 3072; // default for non-Spring unit tests

    @Value("${lorevault.embedding.batch-size:32}")
    private int batchSize = 32; // default for non-Spring unit tests

    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setEmbeddingDim(int embeddingDim) { this.embeddingDim = embeddingDim; }

    @Transactional
    public int generateEmbeddingsForChapter(UUID chapterId) {
        if (batchSize <= 0) batchSize = 32; // normalize if not injected
        if (embeddingDim <= 0) embeddingDim = 3072; // normalize if not injected
        Instant overallStart = Instant.now();
        log.info("[Embeddings] START chapter={}", chapterId);
        List<ChunkNode> chunks = contentPersistencePort.findChunksByChapterId(chapterId);
        log.debug("[Embeddings] Loaded {} chunks ({} ms) chapter={}", chunks.size(), Duration.between(overallStart, Instant.now()).toMillis(), chapterId);
        if (chunks.isEmpty()) {
            log.info("[Embeddings] DONE (no chunks) chapter={} totalMs={}", chapterId, Duration.between(overallStart, Instant.now()).toMillis());
            return 0;
        }
        String modelId = embeddingPort.getModelId();
        Instant selectionStart = Instant.now();
        List<ChunkNode> targets = new ArrayList<>();
        for (ChunkNode c : chunks) {
            String sha = computeEmbeddingHash(modelId, c.getContentHash());
            boolean needs = c.getEmbedding() == null || c.getEmbeddingHash() == null || !c.getEmbeddingHash().equals(sha);
            if (log.isDebugEnabled()) {
                log.debug("[Embeddings] Inspect chunk id={} contentHash={} hasVec={} storedHash={} expectedHash={} needs={}", c.getId(), c.getContentHash(), c.getEmbedding() != null, c.getEmbeddingHash(), sha, needs);
            }
            if (needs) targets.add(c);
        }
        log.debug("[Embeddings] Selection completed in {} ms (targets={} / {}) chapter={}", Duration.between(selectionStart, Instant.now()).toMillis(), targets.size(), chunks.size(), chapterId);
        if (targets.isEmpty()) {
            log.info("[Embeddings] DONE (all up-to-date) chapter={} totalMs={}", chapterId, Duration.between(overallStart, Instant.now()).toMillis());
            return 0;
        }
        log.info("[Embeddings] {} / {} chunks need embeddings (chapter {})", targets.size(), chunks.size(), chapterId);

        List<String> texts = targets.stream().map(ChunkNode::getContentHash).toList(); // TODO: replace with real text reconstruction

        Instant embedStart = Instant.now();
        List<double[]> vectors;
        try {
            vectors = batchEmbed(texts);
        } catch (Exception e) {
            log.error("[Embeddings] Batch embedding failed chapter={} stage=embedding elapsedMs={} error={}", chapterId, Duration.between(embedStart, Instant.now()).toMillis(), e.getMessage(), e);
            return 0;
        }
        long embedMs = Duration.between(embedStart, Instant.now()).toMillis();
        log.info("[Embeddings] Generated {} vectors in {} ms (chapter {})", vectors.size(), embedMs, chapterId);

        Instant persistStart = Instant.now();
        int updated = 0;
        for (int i = 0; i < targets.size(); i++) {
            ChunkNode target = targets.get(i);
            double[] vec = vectors.get(i);
            if (vec.length == 0) continue; // skip failed
            if (embeddingDim > 0 && vec.length != embeddingDim) {
                log.warn("[Embeddings] Dimension mismatch chunkId={} expectedDim={} actualDim={} chapter={}", target.getId(), embeddingDim, vec.length, chapterId);
            }
            target.setEmbedding(vec);
            target.setEmbeddingHash(computeEmbeddingHash(modelId, target.getContentHash()));
            target.setEmbeddedAt(LocalDateTime.now());
            updated++;
        }
        contentPersistencePort.updateChunks(targets);
        long persistMs = Duration.between(persistStart, Instant.now()).toMillis();
        long totalMs = Duration.between(overallStart, Instant.now()).toMillis();
        log.info("[Embeddings] Persisted {} updated embeddings (persistMs={} totalMs={}) chapter={}", updated, persistMs, totalMs, chapterId);
        return updated;
    }

    private List<double[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Instant start = Instant.now();
        List<double[]> all = new ArrayList<>(texts.size());
        int effectiveBatchSize = batchSize > 0 ? batchSize : texts.size();
        if (effectiveBatchSize <= 0) {
            effectiveBatchSize = 1; // final safeguard
        }
        int totalBatches = (texts.size() + effectiveBatchSize - 1) / effectiveBatchSize;
        for (int i = 0; i < texts.size(); i += effectiveBatchSize) {
            int end = Math.min(i + effectiveBatchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            Instant batchStart = Instant.now();
            List<double[]> embedded = embeddingPort.embedBatch(batch);
            long batchMs = Duration.between(batchStart, Instant.now()).toMillis();
            all.addAll(embedded);
            if (log.isDebugEnabled()) {
                log.debug("[Embeddings] BatchProgress batchIndex={} of {} size={} batchMs={} accumulated={} firstVecPreview={}", (i / effectiveBatchSize) + 1, totalBatches, batch.size(), batchMs, all.size(), embedded.isEmpty() ? "n/a" : preview(embedded.get(0)));
            }
        }
        long totalMs = Duration.between(start, Instant.now()).toMillis();
        log.debug("[Embeddings] batchEmbed completed vectors={} totalMs={} avgPerVecMs={}", all.size(), totalMs, all.isEmpty() ? 0 : (double) totalMs / all.size());
        return all;
    }

    private String preview(double[] vec) {
        int show = Math.min(5, vec.length);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < show; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("%.4f", vec[i]));
        }
        if (vec.length > show) sb.append("…");
        sb.append(']');
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<org.springframework.ai.document.Document> search(String query, int limit, double threshold) {
        return List.of();
    }

    private String computeEmbeddingHash(String modelId, String contentHash) {
        String material = modelId + ":" + contentHash;
        return sha256(material);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
