package com.lorevault.api.service.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Metrics;

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
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkEmbeddingService {

    private final ContentPersistencePort contentPersistencePort; // for chunk retrieval & persistence
    private final EmbeddingPort embeddingPort; // generation port

    @Value("${lorevault.embedding.dim:1536}")
    private int embeddingDim = 1536; // default for non-Spring unit tests

    @Value("${lorevault.embedding.batch-size:32}")
    private int batchSize = 32; // default for non-Spring unit tests

    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setEmbeddingDim(int embeddingDim) { this.embeddingDim = embeddingDim; }

    // =====================================
    // Public API
    // =====================================

    @Transactional
    public int generateEmbeddingsForChapter(UUID chapterId) {
        normalizeConfiguration();
        
        EmbeddingContext context = new EmbeddingContext(chapterId, Instant.now());
        
        log.info("[Embeddings] START chapter={}", chapterId);
        
        List<ChunkNode> chunks = loadChunks(context);
        if (chunks.isEmpty()) {
            return finishWithNoWork(context, "no chunks");
        }
        
        List<ChunkNode> targets = selectTargetsNeedingEmbedding(chunks, context);
        if (targets.isEmpty()) {
            return finishWithNoWork(context, "all up-to-date", chunks.size());
        }
        
        log.info("[Embeddings] {} / {} chunks need embeddings (chapter {})", targets.size(), chunks.size(), chapterId);
        
        List<String> texts = extractTextsForEmbedding(targets, chapterId);
        List<double[]> vectors = generateVectors(texts, context);
        if (vectors.isEmpty()) {
            return 0; // Error already logged and metrics recorded
        }
        
        int updated = updateChunksWithEmbeddings(targets, vectors, context);
        recordFinalMetrics(context, updated, targets.size() - updated);
        
        return updated;
    }

    @Transactional(readOnly = true)
    public List<org.springframework.ai.document.Document> search(String query, int limit, double threshold) {
        return List.of();
    }

    // =====================================
    // Context and Helper Classes  
    // =====================================

    private static class EmbeddingContext {
        final UUID chapterId;
        final Instant startTime;
        long embeddingTimeMs = 0;

        EmbeddingContext(UUID chapterId, Instant startTime) {
            this.chapterId = chapterId;
            this.startTime = startTime;
        }

        long elapsedMs() {
            return Duration.between(startTime, Instant.now()).toMillis();
        }

        void recordEmbeddingTime(long ms) {
            this.embeddingTimeMs = ms;
        }
    }

    // =====================================
    // Private Helper Methods
    // =====================================

    private void normalizeConfiguration() {
        if (batchSize <= 0) batchSize = 32;
        if (embeddingDim <= 0) embeddingDim = 3072;
    }

    private List<ChunkNode> loadChunks(EmbeddingContext context) {
        List<ChunkNode> chunks = contentPersistencePort.findChunksByChapterId(context.chapterId);
        long elapsed = context.elapsedMs();
        log.debug("[Embeddings] Loaded {} chunks ({} ms) chapter={}", chunks.size(), elapsed, context.chapterId);
        return chunks;
    }

    private int finishWithNoWork(EmbeddingContext context, String reason) {
        return finishWithNoWork(context, reason, 0);
    }

    private int finishWithNoWork(EmbeddingContext context, String reason, int skippedCount) {
        long totalMs = context.elapsedMs();
        log.info("[Embeddings] DONE ({}) chapter={} totalMs={}", reason, context.chapterId, totalMs);
        if (skippedCount > 0) {
            Metrics.counter("embeddings.skipped.count", "reason", "up_to_date").increment(skippedCount);
        }
        Metrics.timer("embeddings.process.duration").record(totalMs, TimeUnit.MILLISECONDS);
        return 0;
    }

    private List<ChunkNode> selectTargetsNeedingEmbedding(List<ChunkNode> chunks, EmbeddingContext context) {
        Instant selectionStart = Instant.now();
        String modelId = embeddingPort.getModelId();
        List<ChunkNode> targets = new ArrayList<>();
        
        for (ChunkNode chunk : chunks) {
            if (chunkNeedsEmbedding(chunk, modelId)) {
                targets.add(chunk);
            }
        }
        
        long selectionMs = Duration.between(selectionStart, Instant.now()).toMillis();
        log.debug("[Embeddings] Selection completed in {} ms (targets={} / {}) chapter={}", 
                selectionMs, targets.size(), chunks.size(), context.chapterId);
        
        return targets;
    }

    private boolean chunkNeedsEmbedding(ChunkNode chunk, String modelId) {
        String expectedHash = computeEmbeddingHash(modelId, chunk.getContentHash());
        boolean needs = chunk.getEmbedding() == null || 
                       chunk.getEmbeddingHash() == null || 
                       !chunk.getEmbeddingHash().equals(expectedHash);
        
        if (log.isDebugEnabled()) {
            log.debug("[Embeddings] Inspect chunk id={} contentHash={} hasVec={} storedHash={} expectedHash={} needs={}", 
                    chunk.getId(), chunk.getContentHash(), chunk.getEmbedding() != null, 
                    chunk.getEmbeddingHash(), expectedHash, needs);
        }
        
        return needs;
    }

    private List<String> extractTextsForEmbedding(List<ChunkNode> targets, UUID chapterId) {
        String rawText = loadChapterRawText(chapterId);
        List<String> texts = new ArrayList<>(targets.size());
        
        for (ChunkNode target : targets) {
            String text = extractTextForChunk(target, rawText);
            texts.add(text == null ? "" : text);
        }
        
        return texts;
    }

    private String loadChapterRawText(UUID chapterId) {
        try {
            return contentPersistencePort.findChapterById(chapterId)
                    .map(ChapterNode::getRawText)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[Embeddings] Failed to load chapter rawText chapter={} error={}", chapterId, e.getMessage());
            return null;
        }
    }

    private String extractTextForChunk(ChunkNode chunk, String rawText) {
        // Prefer directly stored chunk text when available (decouples from chapter/scene)
        if (chunk.getText() != null && !chunk.getText().isEmpty()) {
            return chunk.getText();
        }
        
        // Fallback to coordinate-based extraction
        if (rawText != null && chunk.getStartCharInChapter() != null && chunk.getEndCharInChapter() != null) {
            return extractTextByCoordinates(chunk, rawText);
        }
        
        // Final fallback to content hash
        log.debug("[Embeddings] Using contentHash fallback for chunk {}", chunk.getId());
        return chunk.getContentHash();
    }

    private String extractTextByCoordinates(ChunkNode chunk, String rawText) {
        int start = chunk.getStartCharInChapter();
        int end = chunk.getEndCharInChapter();
        
        if (start < 0 || end > rawText.length() || start >= end) {
            log.warn("[Embeddings] Invalid offsets chunkId={} start={} end={} len={} fallback=hash", 
                    chunk.getId(), start, end, rawText.length());
            return chunk.getContentHash();
        }
        
        try {
            return rawText.substring(start, end);
        } catch (Exception ex) {
            log.warn("[Embeddings] Substring extraction failed chunkId={} start={} end={} len={} error={} fallback=hash", 
                    chunk.getId(), start, end, rawText.length(), ex.getMessage());
            return chunk.getContentHash();
        }
    }

    private List<double[]> generateVectors(List<String> texts, EmbeddingContext context) {
        if (texts.isEmpty()) {
            return List.of();
        }
        
        Instant embedStart = Instant.now();
        try {
            List<double[]> vectors = batchEmbed(texts);
            long embedMs = Duration.between(embedStart, Instant.now()).toMillis();
            log.info("[Embeddings] Generated {} vectors in {} ms (chapter {})", 
                    vectors.size(), embedMs, context.chapterId);
            context.recordEmbeddingTime(embedMs);
            return vectors;
        } catch (Exception e) {
            long elapsed = Duration.between(embedStart, Instant.now()).toMillis();
            log.error("[Embeddings] Batch embedding failed chapter={} stage=embedding elapsedMs={} error={}", 
                    context.chapterId, elapsed, e.getMessage(), e);
            Metrics.counter("embeddings.skipped.count", "reason", "batch_failure").increment(texts.size());
            Metrics.timer("embeddings.process.duration").record(context.elapsedMs(), TimeUnit.MILLISECONDS);
            return List.of();
        }
    }

    private int updateChunksWithEmbeddings(List<ChunkNode> targets, List<double[]> vectors, EmbeddingContext context) {
        Instant persistStart = Instant.now();
        String modelId = embeddingPort.getModelId();
        int updated = 0;
        
        for (int i = 0; i < targets.size(); i++) {
            ChunkNode target = targets.get(i);
            double[] vector = vectors.get(i);
            
            if (updateChunkWithVector(target, vector, modelId)) {
                updated++;
            }
        }
        
        contentPersistencePort.updateChunks(targets);
        
        long persistMs = Duration.between(persistStart, Instant.now()).toMillis();
        long totalMs = context.elapsedMs();
        
        log.info("[Embeddings] Persisted {} updated embeddings (persistMs={} totalMs={}) chapter={}", 
                updated, persistMs, totalMs, context.chapterId);
        
        return updated;
    }

    private boolean updateChunkWithVector(ChunkNode chunk, double[] vector, String modelId) {
        if (vector.length == 0) {
            return false; // Skip failed embeddings
        }
        
        if (embeddingDim > 0 && vector.length != embeddingDim) {
            log.warn("[Embeddings] Dimension mismatch chunkId={} expectedDim={} actualDim={}", 
                    chunk.getId(), embeddingDim, vector.length);
        }
        
        chunk.setEmbedding(vector);
        chunk.setEmbeddingHash(computeEmbeddingHash(modelId, chunk.getContentHash()));
        chunk.setEmbeddedAt(LocalDateTime.now());
        
        return true;
    }

    private void recordFinalMetrics(EmbeddingContext context, int updated, int skipped) {
        if (updated > 0) {
            Metrics.counter("embeddings.generated.count").increment(updated);
        }
        if (skipped > 0) {
            Metrics.counter("embeddings.skipped.count", "reason", "zero_length").increment(skipped);
        }
        
        long totalMs = context.elapsedMs();
        Metrics.timer("embeddings.process.duration").record(totalMs, TimeUnit.MILLISECONDS);
        
        if (context.embeddingTimeMs > 0) {
            Metrics.timer("embeddings.embedding.phase.duration").record(context.embeddingTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    // =====================================
    // Embedding Generation
    // =====================================

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

    // =====================================
    // Utility Methods
    // =====================================

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
