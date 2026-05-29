package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.embedding.EmbeddingFailure;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import com.lorevault.api.content.association.ChapterEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Embeds {@code ChapterEvent.aggregateCard} text into dense vectors using the shared embedding
 * model.  Mirrors the hash-based freshness check pattern from {@code EmbeddingService} — an event
 * is only re-embedded when its {@code embeddingHash} (SHA-256 of {@code modelId:aggregateCard})
 * differs from the stored value.
 *
 * <p><b>Transaction discipline:</b> this service is intentionally non-transactional.  DB I/O is
 * delegated to {@link ChapterEventEmbeddingTransactionSupport} so the external embedding API call
 * happens outside any DB transaction boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterEventEmbeddingService {

    private final ChapterEventEmbeddingTransactionSupport txSupport;
    private final EmbeddingModel embeddingModel;

    @Value("${lorevault.embedding.model.dimensions:1536}")
    private int embeddingDim = 1536;

    @Value("${lorevault.embedding.model.batch-size:32}")
    private int batchSize = 32;

    @Value("${lorevault.ai.models.embedding.model:}")
    private String configuredEmbeddingModelId = "";

    private volatile String runtimeResolvedModelId = "";

    // For testing
    void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    void setEmbeddingDim(int embeddingDim) { this.embeddingDim = embeddingDim; }
    void setConfiguredEmbeddingModelId(String id) { this.configuredEmbeddingModelId = id; }

    /**
     * Embed all ChapterEvent nodes for the given chapter that lack a current embedding.
     *
     * @return the number of events that were actually (re)embedded
     */
    public int embedChapterEvents(UUID chapterId) {
        normalizeConfig();

        log.info("[EventEmbeddings] START chapter={}", chapterId);

        List<ChapterEvent> allEvents = txSupport.loadChapterEvents(chapterId);
        if (allEvents.isEmpty()) {
            log.info("[EventEmbeddings] No ChapterEvents found chapter={}", chapterId);
            return 0;
        }

        String modelId = resolveModelId();
        List<ChapterEvent> targets = selectTargets(allEvents, modelId);
        if (targets.isEmpty()) {
            log.info("[EventEmbeddings] All {} events are up-to-date chapter={}", allEvents.size(), chapterId);
            return 0;
        }

        log.info("[EventEmbeddings] {} / {} events need embedding chapter={}", targets.size(), allEvents.size(), chapterId);

        List<String> texts = extractTexts(targets);
        List<double[]> vectors = generateVectors(texts, chapterId);
        validateVectorCount(texts, vectors, chapterId);

        List<ChapterEvent> updated = applyVectors(targets, vectors, modelId);
        txSupport.saveChapterEvents(updated);

        log.info("[EventEmbeddings] Persisted {} embeddings chapter={}", updated.size(), chapterId);
        return updated.size();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void normalizeConfig() {
        if (batchSize <= 0) batchSize = 32;
        if (embeddingDim <= 0) embeddingDim = 1536;
    }

    private List<ChapterEvent> selectTargets(List<ChapterEvent> events, String modelId) {
        List<ChapterEvent> targets = new ArrayList<>();
        for (ChapterEvent event : events) {
            if (needsEmbedding(event, modelId)) {
                targets.add(event);
            }
        }
        return targets;
    }

    private boolean needsEmbedding(ChapterEvent event, String modelId) {
        if (event.aggregateCard() == null || event.aggregateCard().isBlank()) {
            return false; // nothing to embed
        }
        String expected = computeEmbeddingHash(modelId, event.aggregateCard());
        return event.embedding() == null
                || event.embeddingHash() == null
                || !event.embeddingHash().equals(expected)
                || !hasExpectedDimensions(event.embedding());
    }

    private List<String> extractTexts(List<ChapterEvent> targets) {
        List<String> texts = new ArrayList<>(targets.size());
        for (ChapterEvent event : targets) {
            texts.add(event.aggregateCard() != null ? event.aggregateCard() : "");
        }
        return texts;
    }

    private List<double[]> generateVectors(List<String> texts, UUID chapterId) {
        try {
            return batchEmbed(texts);
        } catch (EmbeddingGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[EventEmbeddings] Batch embedding failed chapter={} error={}", chapterId, sanitizeMessage(e), e);
            EmbeddingFailure failure = EmbeddingFailure.builder(
                            "EVENT_EMBEDDING_BACKEND_UNAVAILABLE",
                            "Embedding backend failed while generating ChapterEvent vectors")
                    .exceptionType(EmbeddingGenerationException.class.getSimpleName())
                    .stage("EVENT_EMBEDDING")
                    .detail("chapterId", chapterId)
                    .detail("count", texts.size())
                    .build();
            throw new EmbeddingGenerationException(failure, e);
        }
    }

    private List<ChapterEvent> applyVectors(List<ChapterEvent> targets, List<double[]> vectors, String modelId) {
        List<ChapterEvent> updated = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            ChapterEvent original = targets.get(i);
            double[] vector = vectors.get(i);
            validateVectorDimensions(original.id(), vector);
            String newHash = computeEmbeddingHash(modelId, original.aggregateCard());
            ChapterEvent embedded = new ChapterEvent(
                    original.id(),
                    original.chapterId(),
                    original.stageId(),
                    original.componentId(),
                    original.displayName(),
                    original.normalizedName(),
                    original.representativeEventType(),
                    original.mentionCount(),
                    original.aggregateCard(),
                    original.supportedAliases(),
                    original.supportedEventTypes(),
                    original.identityEvidence(),
                    original.createdAt(),
                    original.updatedAt(),
                    vector,
                    newHash,
                    LocalDateTime.now()
            );
            updated.add(embedded);
        }
        return updated;
    }

    private boolean hasExpectedDimensions(double[] vector) {
        return vector != null && vector.length == embeddingDim;
    }

    private void validateVectorDimensions(UUID eventId, double[] vector) {
        int actual = vector == null ? 0 : vector.length;
        if (actual == embeddingDim) {
            return;
        }

        EmbeddingFailure failure = EmbeddingFailure.builder(
                        "EVENT_EMBEDDING_BACKEND_UNAVAILABLE",
                        "Embedding backend returned a ChapterEvent vector with unexpected dimensions")
                .exceptionType(EmbeddingGenerationException.class.getSimpleName())
                .stage("EVENT_EMBEDDING")
                .detail("eventId", eventId)
                .detail("expectedDimensions", embeddingDim)
                .detail("actualDimensions", actual)
                .build();
        throw new EmbeddingGenerationException(failure);
    }

    private void validateVectorCount(List<String> texts, List<double[]> vectors, UUID chapterId) {
        if (vectors.size() == texts.size()) {
            return;
        }
        EmbeddingFailure failure = EmbeddingFailure.builder(
                        "EVENT_EMBEDDING_BACKEND_UNAVAILABLE",
                        "Embedding backend returned a different number of vectors than requested")
                .exceptionType(EmbeddingGenerationException.class.getSimpleName())
                .stage("EVENT_EMBEDDING")
                .detail("chapterId", chapterId)
                .detail("requestedCount", texts.size())
                .detail("actualCount", vectors.size())
                .build();
        throw new EmbeddingGenerationException(failure);
    }

    // ── embedding batch ───────────────────────────────────────────────────────

    private List<double[]> batchEmbed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        int effectiveBatch = batchSize > 0 ? batchSize : texts.size();
        List<double[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += effectiveBatch) {
            int end = Math.min(i + effectiveBatch, texts.size());
            List<String> batch = texts.subList(i, end);
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
            rememberRuntimeModelId(response);
            response.getResults().stream()
                    .map(e -> toDoubleArray(e.getOutput()))
                    .forEach(all::add);
        }
        return all;
    }

    private void rememberRuntimeModelId(EmbeddingResponse response) {
        if (response == null || response.getMetadata() == null) return;
        String modelId = response.getMetadata().getModel();
        if (modelId != null && !modelId.isBlank()) {
            runtimeResolvedModelId = modelId;
        }
    }

    private double[] toDoubleArray(float[] vector) {
        if (vector == null) return new double[0];
        double[] out = new double[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i];
        return out;
    }

    // ── ID / hash helpers ─────────────────────────────────────────────────────

    private String resolveModelId() {
        if (configuredEmbeddingModelId != null && !configuredEmbeddingModelId.isBlank()) {
            return configuredEmbeddingModelId;
        }
        if (runtimeResolvedModelId != null && !runtimeResolvedModelId.isBlank()) {
            return runtimeResolvedModelId;
        }
        String className = embeddingModel.getClass().getName();
        return className.contains("OpenAiEmbeddingModel") ? "openai-compatible-embedding" : className;
    }

    String computeEmbeddingHash(String modelId, String aggregateCard) {
        String material = modelId + ":" + aggregateCard;
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
