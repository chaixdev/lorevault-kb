package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ai.llm.LlmCallLogger;
import com.lorevault.api.config.LoreVaultLlmLoggingProperties;
import com.lorevault.api.ingestion.orchestration.StageGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StageStatus;
import com.lorevault.api.ingestion.resolution.event.LlmCallRecord;
import com.lorevault.api.ingestion.resolution.event.LlmCallRequest;
import com.lorevault.api.ingestion.resolution.event.LlmCallResponse;
import com.lorevault.api.ingestion.orchestration.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LlmCallLoggingService implements LlmCallLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLoggingService.class);

    private final LoreVaultLlmLoggingProperties props;
    private final StageGraphRepository stageRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;

    public LlmCallLoggingService(LoreVaultLlmLoggingProperties props,
                                 StageGraphRepository stageRepo,
                                 LlmCallRecordGraphRepository llmCallRepo) {
        this.props = props;
        this.stageRepo = stageRepo;
        this.llmCallRepo = llmCallRepo;
    }

    @Override
    public void logCall(
            UUID jobId,
            StageKey stage,
            String provider,
            String model,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String promptTemplateId,
            String renderedPrompt,
            String inputBody,
            String responseBody,
            long latencyMs,
            Integer inputTokensEst,
            Integer outputTokensEst
    ) {
        String step = stage.name().toLowerCase().replace('_', '-');

        if (jobId == null) {
            log.debug("[LLM-LOG] Missing jobId; skipping persistence for step={}", step);
            return;
        }
        if (props.enabled() == Boolean.FALSE) {
            return;
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
        Integer maxBodyChars = props.maxBodyChars();
        TruncationResult inputResult = maybeTruncate(inputBody, maxBodyChars);
        LlmCallRequest request = new LlmCallRequest();
        request.setId(UUID.randomUUID());
        request.setRenderedPrompt(props.storeRenderedPrompt() ? renderedPrompt : null);
        request.setInputBody(inputResult.body());
        request.setInputHash(inputResult.hash());
        request.setInputTruncated(inputResult.truncated());
        rec.setRequest(request);

        TruncationResult responseResult = props.persistBodiesEnabled() == Boolean.TRUE
                ? maybeTruncate(responseBody, maxBodyChars)
                : TruncationResult.notPersisted();
        LlmCallResponse response = new LlmCallResponse();
        response.setId(UUID.randomUUID());
        response.setBody(responseResult.body());
        response.setBodyHash(responseResult.hash());
        response.setTruncated(responseResult.truncated());
        rec.setResponse(response);

        rec.setCreatedAt(LocalDateTime.now());

        // Attach to current Stage if available — look up by job+stage
        try {
            var stageOpt = stageRepo.findByJobIdAndStep(jobId, stage);
            if (stageOpt.isPresent()) {
                Stage cur = stageOpt.get();
                rec.setStageId(cur.getId());
                rec.setStage(cur);
                log.debug("[LLM-LOG] Linking LLM call step={} to stage {}", step, cur.getId());
            } else {
                // Fallback: find any RUNNING stage for this job
                var stages = stageRepo.findByJobId(jobId);
                stages.stream()
                        .filter(s -> s.getStatus() == StageStatus.RUNNING)
                        .findFirst()
                        .ifPresent(s -> {
                            rec.setStageId(s.getId());
                            rec.setStage(s);
                            log.debug("[LLM-LOG] Linking LLM call step={} to running stage {} (fallback)", step, s.getId());
                        });
            }
        } catch (Exception e) {
            log.debug("[LLM-LOG] Unable to resolve current stage for job {}: {}", jobId, e.getMessage());
        }

        llmCallRepo.save(rec);
    }

    private TruncationResult maybeTruncate(String body, Integer maxChars) {
        if (body == null) {
            return new TruncationResult(null, null, false);
        }
        if (maxChars == null || maxChars < 0 || body.length() <= maxChars) {
            return new TruncationResult(body, sha256(body), false);
        }
        return new TruncationResult(body.substring(0, maxChars), sha256(body), true);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte current : hashed) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private record TruncationResult(String body, String hash, Boolean truncated) {
        private static TruncationResult notPersisted() {
            return new TruncationResult(null, null, null);
        }
    }
}
