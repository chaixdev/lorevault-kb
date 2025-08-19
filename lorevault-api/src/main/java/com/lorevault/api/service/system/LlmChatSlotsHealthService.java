package com.lorevault.api.service.system;

import com.lorevault.api.configuration.properties.LoreVaultModelsProperties;
import com.lorevault.api.service.system.metrics.HealthMetricsCollector;
import com.lorevault.api.service.system.retry.RetryableHealthChecker;
import com.lorevault.api.service.system.validator.ModelHealthValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health checker that validates both chat slots (nlp-small and nlp-big).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmChatSlotsHealthService {

    @Qualifier("nlpSmall")
    private final ChatClient nlpSmallChatClient;
    @Qualifier("nlpBig")
    private final ChatClient nlpBigChatClient;
    private final LoreVaultModelsProperties models;
    private final RetryableHealthChecker retryableHealthChecker;
    private final ModelHealthValidator modelHealthValidator;

    public Map<String, HealthMetricsCollector.ModelHealthStatus> checkSlots() {
        Map<String, HealthMetricsCollector.ModelHealthStatus> results = new LinkedHashMap<>();
        results.put("nlp-small", checkOne(nlpSmallChatClient, models.nlpSmall().model()));
        results.put("nlp-big", checkOne(nlpBigChatClient, models.nlpBig().model()));
        return results;
    }

    private HealthMetricsCollector.ModelHealthStatus checkOne(ChatClient client, String modelId) {
        String op = "health-check-" + modelId;
        RetryableHealthChecker.RetryConfig cfg = RetryableHealthChecker.RetryConfig.defaultConfig();
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> rr =
            retryableHealthChecker.executeWithRetry(op, cfg, () -> {
                try {
                    return modelHealthValidator.performConnectivityTestWith(client, modelId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        if (rr.isSuccess()) {
            return new HealthMetricsCollector.ModelHealthStatus(true, modelId, "OK", rr.getLastAttemptDurationMs(), rr.getTotalDurationMs(), rr.getAttemptsUsed());
        }
        String msg = rr.getLastException() != null ? rr.getLastException().getMessage() : "Unknown error";
        return new HealthMetricsCollector.ModelHealthStatus(false, modelId, msg, rr.getLastAttemptDurationMs(), rr.getTotalDurationMs(), rr.getAttemptsUsed());
    }
}
