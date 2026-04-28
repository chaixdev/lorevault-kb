package com.lorevault.api.ai.embedding;

import com.lorevault.api.content.chunk.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.embedding.EmbeddingModel;
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
public class EmbeddingService {

    private final EmbeddingTransactionSupport txSupport;
    private final EmbeddingModel embeddingModel;

    @Value("${lorevault.embedding.model.dimensions:2560}")
    private int embeddingDim = 2560; // default for non-Spring unit tests

    @Value("${lorevault.embedding.model.batch-size:32}")
    private int batchSize = 32; // default for non-Spring unit tests

    @Value("${lorevault.ai.models.embedding.model:}")
    private String configuredEmbeddingModelId = "";

    private volatile String runtimeResolvedModelId = "";

    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setEmbeddingDim(int embeddingDim) { this.embeddingDim = embeddingDim; }
    public void setConfiguredEmbeddingModelId(String configuredEmbeddingModelId) { this.configuredEmbeddingModelId = configuredEmbeddingModelId; }

    // =====================================
    // Public API
    // =====================================

    public int generateEmbeddingsForChapter(UUID chapterId) {
        normalizeConfiguration();
        
        EmbeddingContext context = new EmbeddingContext(chapterId, Instant.now());
        
        log.info("[Embeddings] START chapter={}", chapterId);
        
        List<Chunk> chunks = loadChunks(context);
        if (chunks.isEmpty()) {
            return finishWithNoWork(context, "no chunks");
        }
        
        List<Chunk> targets = selectTargetsNeedingEmbedding(chunks, context);
        if (targets.isEmpty()) {
            return finishWithNoWork(context, "all up-to-date", chunks.size());
        }
        
        log.info("[Embeddings] {} / {} chunks need embeddings (chapter {})", targets.size(), chunks.size(), chapterId);
        
        List<String> texts = extractTextsForEmbedding(targets, chapterId);
        List<double[]> vectors = generateVectors(texts, context);

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
        if (embeddingDim <= 0) embeddingDim = 2560;
    }

    private List<Chunk> loadChunks(EmbeddingContext context) {
        List<Chunk> chunks = txSupport.loadChunks(context.chapterId);
        long elapsed = context.elapsedMs();
        log.debug("[Embeddings] Loaded {} chunks ({} ms) chapter={}", chunks.size(), elapsed, context.chapterId);
        return chunks;
    }

    private int finishWithNoWork(EmbeddingContext context, String reason) {
        return finishWithNoWork(context, reason, 0);
    }

    private int finishWithNoWork(EmbeddingContext context, String reason, int skippedCount) {
        long totalMs = context.elapsedMs();
        log.info("[Embeddings] DONE ({}) chapter={} totalMs={}", context.chapterId, totalMs);
        if (skippedCount > 0) {
            Metrics.counter("embeddings.skipped.count", "reason", "up_to_date").increment(skippedCount);
        }
        Metrics.timer("embeddings.process.duration").record(totalMs, TimeUnit.MILLISECONDS);
        return 0;
    }

    private List<Chunk> selectTargetsNeedingEmbedding(List<Chunk> chunks, EmbeddingContext context) {
        Instant selectionStart = Instant.now();
        String modelId = resolveModelId();
        List<Chunk> targets = new ArrayList<>();
        
        for (Chunk chunk : chunks) {
            if (chunkNeedsEmbedding(chunk, modelId)) {
                targets.add(chunk);
            }
        }
        
        long selectionMs = Duration.between(selectionStart, Instant.now()).toMillis();
        log.debug("[Embeddings] Selection completed in {} ms (targets={} / {}) chapter={}", 
                selectionMs, targets.size(), chunks.size(), context.chapterId);
        
        return targets;
    }

    private boolean chunkNeedsEmbedding(Chunk chunk, String modelId) {
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

    private List<String> extractTextsForEmbedding(List<Chunk> targets, UUID chapterId) {
        String rawText = loadChapterRawText(chapterId);
        List<String> texts = new ArrayList<>(targets.size());
        
        for (Chunk target : targets) {
            String text = extractTextForChunk(target, rawText);
            texts.add(text == null ? "" : text);
        }
        
        return texts;
    }

    private String loadChapterRawText(UUID chapterId) {
        return txSupport.loadChapterRawText(chapterId);
    }

    private String extractTextForChunk(Chunk chunk, String rawText) {
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

    private String extractTextByCoordinates(Chunk chunk, String rawText) {
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
            validateEmbeddingResult(texts, vectors, context.chapterId, embedMs);
            log.info("[Embeddings] Generated {} vectors in {} ms (chapter {})", 
                    vectors.size(), embedMs, context.chapterId);
            context.recordEmbeddingTime(embedMs);
            return vectors;
        } catch (EmbeddingGenerationException e) {
            throw e;
        } catch (Exception e) {
            long elapsed = Duration.between(embedStart, Instant.now()).toMillis();
            log.error("[Embeddings] Batch embedding failed chapter={} stage=embedding elapsedMs={} error={}", 
                    context.chapterId, elapsed, e.getMessage(), e);
            Metrics.counter("embeddings.skipped.count", "reason", "batch_failure").increment(texts.size());
            Metrics.timer("embeddings.process.duration").record(context.elapsedMs(), TimeUnit.MILLISECONDS);
            throw embeddingFailure(
                    "EMBEDDING_BACKEND_UNAVAILABLE",
                    "Embedding backend failed while generating chunk vectors",
                    context.chapterId,
                    texts.size(),
                    elapsed,
                    e
            );
        }
    }

    private void validateEmbeddingResult(List<String> texts,
                                         List<double[]> vectors,
                                         UUID chapterId,
                                         long elapsedMs) {
        if (vectors == null || vectors.isEmpty()) {
            throw embeddingFailure(
                    "EMBEDDING_RESPONSE_EMPTY",
                    "Embedding backend returned no vectors for requested chunks",
                    chapterId,
                    texts.size(),
                    elapsedMs,
                    null
            );
        }

        if (vectors.size() != texts.size()) {
            throw embeddingFailure(
                    "EMBEDDING_RESPONSE_COUNT_MISMATCH",
                    "Embedding backend returned a different number of vectors than requested chunks",
                    chapterId,
                    texts.size(),
                    elapsedMs,
                    null
            );
        }
    }

    private EmbeddingGenerationException embeddingFailure(String code,
                                                         String message,
                                                         UUID chapterId,
                                                         int chunkCount,
                                                         long elapsedMs,
                                                         Throwable cause) {
        EmbeddingFailure failure = EmbeddingFailure.builder(code, message)
                .exceptionType(EmbeddingGenerationException.class.getSimpleName())
                .stage("EMBEDDING")
                .detail("chapterId", chapterId)
                .detail("chunkCount", chunkCount)
                .detail("elapsedMs", elapsedMs)
                .build();
        return new EmbeddingGenerationException(failure, cause);
    }

    private int updateChunksWithEmbeddings(List<Chunk> targets, List<double[]> vectors, EmbeddingContext context) {
        Instant persistStart = Instant.now();
        String modelId = resolveModelId();
        int updated = 0;
        
        for (int i = 0; i < targets.size(); i++) {
            Chunk target = targets.get(i);
            double[] vector = vectors.get(i);
            
            if (updateChunkWithVector(target, vector, modelId)) {
                updated++;
            }
        }
        
        txSupport.saveChunks(targets);
        
        long persistMs = Duration.between(persistStart, Instant.now()).toMillis();
        long totalMs = context.elapsedMs();
        
        log.info("[Embeddings] Persisted {} updated embeddings (persistMs={} totalMs={}) chapter={}", 
                updated, persistMs, totalMs, context.chapterId);
        
        return updated;
    }

    private boolean updateChunkWithVector(Chunk chunk, double[] vector, String modelId) {
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
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
            rememberRuntimeModelId(response);
            List<double[]> embedded = response.getResults().stream()
                    .map(embedding -> toDoubleArray(embedding.getOutput()))
                    .toList();
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

    private void rememberRuntimeModelId(EmbeddingResponse response) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        String modelId = response.getMetadata().getModel();
        if (modelId != null && !modelId.isBlank()) {
            runtimeResolvedModelId = modelId;
        }
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

    private double[] toDoubleArray(float[] vector) {
        if (vector == null) {
            return new double[0];
        }
        double[] out = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = vector[i];
        }
        return out;
    }

    private String resolveModelId() {
        if (configuredEmbeddingModelId != null && !configuredEmbeddingModelId.isBlank()) {
            return configuredEmbeddingModelId;
        }

        if (runtimeResolvedModelId != null && !runtimeResolvedModelId.isBlank()) {
            return runtimeResolvedModelId;
        }

        String className = embeddingModel.getClass().getName();
        String marker = "OpenAiEmbeddingModel";
        if (className.contains(marker)) {
            return "openai-compatible-embedding";
        }
        return className;
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
