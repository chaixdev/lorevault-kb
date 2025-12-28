package com.lorevault.api.service.ingestion;

import com.lorevault.api.configuration.properties.LoreVaultLlmLoggingProperties;
import com.lorevault.api.domain.ingestion.LlmCallRecord;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.IngestionJobGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.LlmCallRecordGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.StatusRecordGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmCallLoggingService {

    private final LoreVaultLlmLoggingProperties props;
    private final IngestionJobGraphRepository jobRepo;
    private final StatusRecordGraphRepository statusRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;

    public LlmCallRecord logCall(
            UUID jobId,
            String step,
            String provider,
            String model,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String promptTemplateId,
            String renderedPrompt,
            String inputPreview,
            String responseBody,
            long latencyMs,
            Integer inputTokensEst,
            Integer outputTokensEst
    ) {
        if (jobId == null) {
            log.debug("[LLM-LOG] Missing jobId; skipping persistence for step={}", step);
            return null;
        }
        if (props.enabled() == Boolean.FALSE) {
            return null;
        }

        LlmCallRecord rec = new LlmCallRecord();
        rec.setId(UUID.randomUUID());
        rec.setJobId(jobId);
        rec.setStep(step);
        rec.setProvider(provider);
        rec.setModel(model);
        rec.setTemperature(temperature);
        rec.setTopP(topP);
        rec.setMaxTokens(maxTokens);
        rec.setLatencyMs(latencyMs);
        rec.setInputTokens(inputTokensEst);
        rec.setOutputTokens(outputTokensEst);
        rec.setTokensEstimated(Boolean.TRUE);
        rec.setPromptTemplateId(promptTemplateId);
        rec.setStoreRenderedPrompt(props.storeRenderedPrompt());
        rec.setRenderedPrompt(props.storeRenderedPrompt() ? renderedPrompt : null);
        rec.setInputPreview(safePreview(inputPreview));

        if (props.persistBodiesEnabled() == Boolean.TRUE) {
            TruncationResult tr = maybeTruncate(responseBody, props.maxBodyChars());
            rec.setResponseBody(tr.body());
            rec.setTruncated(tr.truncated());
            rec.setResponseHash(tr.hash());
        } else {
            rec.setResponseBody(null);
            rec.setTruncated(null);
            rec.setResponseHash(null);
        }

        rec.setCreatedAt(LocalDateTime.now());

        // Attach to current StatusRecord if available
        try {
            jobRepo.findByIdWithCurrentStatus(jobId).ifPresent(job -> {
                rec.setJob(job);
                StatusRecord cur = job.getCurrentStatus();
                if (cur != null) {
                    rec.setStatusRecordId(cur.getId());
                    rec.setStatus(cur);
                    log.debug("[LLM-LOG] Linking LLM call step={} to current status {}", step, cur.getId());
                } else {
                    // Fallback: use most recent status from history if current is not populated
                    var history = statusRepo.findStatusHistoryForJob(jobId);
                    if (history != null && !history.isEmpty()) {
                        StatusRecord last = history.get(history.size() - 1);
                        rec.setStatusRecordId(last.getId());
                        rec.setStatus(last);
                        log.debug("[LLM-LOG] Linking LLM call step={} to last status {} (fallback)", step, last.getId());
                    }
                }
            });
        } catch (Exception e) {
            log.debug("[LLM-LOG] Unable to resolve current status for job {}: {}", jobId, e.getMessage());
        }

        return llmCallRepo.save(rec);
    }

    private String safePreview(String s) {
        if (s == null) return null;
        int limit = 1000; // fixed preview cap for inputs
        return s.length() <= limit ? s : s.substring(0, limit);
    }

    private TruncationResult maybeTruncate(String body, Integer maxChars) {
        if (body == null) return new TruncationResult(null, null, false);
        if (maxChars == null || maxChars < 0) {
            return new TruncationResult(body, sha256(body), false);
        }
        if (body.length() <= maxChars) {
            return new TruncationResult(body, sha256(body), false);
        }
        String truncated = body.substring(0, maxChars);
        return new TruncationResult(truncated, sha256(body), true);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record TruncationResult(String body, String hash, boolean truncated) {}
}
