package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ai.domain.LlmCallLogger;
import com.lorevault.api.config.LoreVaultLlmLoggingProperties;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.LlmCallRequest;
import com.lorevault.api.ingestion.domain.LlmCallResponse;
import com.lorevault.api.ingestion.domain.StatusRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class LlmCallLoggingService implements LlmCallLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLoggingService.class);

    private final LoreVaultLlmLoggingProperties props;
    private final IngestionJobGraphRepository jobRepo;
    private final StatusRecordGraphRepository statusRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;

    public LlmCallLoggingService(LoreVaultLlmLoggingProperties props,
                                 IngestionJobGraphRepository jobRepo,
                                 StatusRecordGraphRepository statusRepo,
                                 LlmCallRecordGraphRepository llmCallRepo) {
        this.props = props;
        this.jobRepo = jobRepo;
        this.statusRepo = statusRepo;
        this.llmCallRepo = llmCallRepo;
    }

    @Override
    public void logCall(
            UUID jobId,
            String step,
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
        if (jobId == null) {
            log.debug("[LLM-LOG] Missing jobId; skipping persistence for step={}", step);
            return;
        }
        if (props.enabled() == Boolean.FALSE) {
            return;
        }

        Optional<IngestionJob> jobOpt = jobRepo.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.debug("[LLM-LOG] Job {} not found; skipping persistence for step={}", jobId, step);
            return;
        }
        IngestionJob job = jobOpt.orElseThrow();

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
        LlmCallRequest request = new LlmCallRequest();
        request.setId(UUID.randomUUID());
        request.setRenderedPrompt(props.storeRenderedPrompt() ? renderedPrompt : null);
        request.setInputBody(inputBody);
        rec.setRequest(request);

        LlmCallResponse response = new LlmCallResponse();
        response.setId(UUID.randomUUID());
        response.setBody(props.persistBodiesEnabled() == Boolean.TRUE ? responseBody : null);
        rec.setResponse(response);

        rec.setCreatedAt(LocalDateTime.now());

        // Attach to current StatusRecord if available
        try {
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
        } catch (Exception e) {
            log.debug("[LLM-LOG] Unable to resolve current status for job {}: {}", jobId, e.getMessage());
        }

        llmCallRepo.save(rec);
    }
}
