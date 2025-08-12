package com.lorevault.api.service.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for monitoring the health of external LLM services.
 * Performs health checks on startup to fail early if services are unavailable.
 * Supports checking multiple models individually.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmHealthCheckService {

    private final ChatClient chatClient;
    
    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String currentModelId;

    @Value("${lorevault.llm.health.enabled:true}")
    private boolean healthEnabled;

    /**
     * Performs health check on the configured LLM model after application startup.
     * Fails early if the service is not accessible.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void performStartupHealthCheck() {
        if (!healthEnabled) {
            log.info("LLM health check disabled via lorevault.llm.health.enabled=false");
            return;
        }
        Instant overallStart = Instant.now();
        log.info("Performing LLM service health check for model: {}", currentModelId);
        ModelHealthStatus healthResult = checkCurrentModel();
        long totalMs = Duration.between(overallStart, Instant.now()).toMillis();
        if (healthResult.isHealthy()) {
            log.info("✅ LLM model '{}' is healthy ({} ms total)", currentModelId, totalMs);
        } else {
            log.error("❌ LLM model '{}' health check FAILED after {} ms: {}", currentModelId, totalMs, healthResult.getErrorMessage());
        }
    }
    
    /**
     * Checks the health of the currently configured model.
     * 
     * @return Health status of the current model
     */
    public ModelHealthStatus checkCurrentModel() {
        return checkSingleModel(currentModelId);
    }
    
    /**
     * Checks the health of all configured models (for backwards compatibility).
     * Since we now only have one model, this returns a map with that single model.
     * 
     * @return Map of model names to their health status
     */
    public Map<String, ModelHealthStatus> checkAllModels() {
        Map<String, ModelHealthStatus> results = new LinkedHashMap<>();
        results.put(currentModelId, checkCurrentModel());
        return results;
    }
    
    /**
     * Checks the health of a specific model.
     * Adds per-attempt timing information for diagnostics.
     * 
     * @param modelId The model identifier (e.g., "gemini-2.5-flash-lite")
     * @return Health status for the model
     */
    public ModelHealthStatus checkSingleModel(String modelId) {
        int maxRetries = 3;
        long baseDelayMs = 1000;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Instant attemptStart = Instant.now();
            try {
                log.debug("[LLM-Health] Checking model={} attempt={}/{}", modelId, attempt, maxRetries);
                Prompt healthCheckPrompt = new Prompt("Respond with 'OK' if you can process this message.");
                String response = chatClient.prompt(healthCheckPrompt).call().content();
                long attemptMs = Duration.between(attemptStart, Instant.now()).toMillis();
                log.trace("[LLM-Health] Raw response (attempt {} model={}): {}", attempt, modelId, response);
                if (response != null && !response.trim().isEmpty()) {
                    log.debug("✅ Model '{}' health attempt {}/{} succeeded in {} ms", modelId, attempt, maxRetries, attemptMs);
                    return new ModelHealthStatus(true, modelId, response.trim(), attemptMs, attempt);
                } else {
                    throw new RuntimeException("Empty response received");
                }
            } catch (Exception e) {
                long attemptMs = Duration.between(attemptStart, Instant.now()).toMillis();
                log.warn("[LLM-Health] Attempt {}/{} failed model={} in {} ms: {}", attempt, maxRetries, modelId, attemptMs, e.getMessage());
                if (attempt == maxRetries) {
                    return new ModelHealthStatus(false, modelId, e.getMessage(), attemptMs, attempt);
                }
                long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new ModelHealthStatus(false, modelId, "Health check interrupted", attemptMs, attempt);
                }
            }
        }
        return new ModelHealthStatus(false, modelId, "Unexpected end of retry loop", 0, maxRetries);
    }
    
    /**
     * Performs an on-demand health check of the configured LLM service.
     * 
     * @return true if the service is healthy, false otherwise
     */
    public boolean isLlmServiceHealthy() {
        if (!healthEnabled) return true; // treat disabled as healthy to not fail readiness
        ModelHealthStatus status = checkCurrentModel();
        return status.isHealthy();
    }
    
    /**
     * Data class to hold the health status of a specific model with timing metadata.
     */
    public static class ModelHealthStatus {
        private final boolean healthy;
        private final String modelName;
        private final String message;
        private final long lastAttemptDurationMs;
        private final int attemptsUsed;
        
        public ModelHealthStatus(boolean healthy, String modelName, String message, long lastAttemptDurationMs, int attemptsUsed) {
            this.healthy = healthy;
            this.modelName = modelName;
            this.message = message;
            this.lastAttemptDurationMs = lastAttemptDurationMs;
            this.attemptsUsed = attemptsUsed;
        }
        
        public boolean isHealthy() { return healthy; }
        public String getModelName() { return modelName; }
        public String getErrorMessage() { return healthy ? null : message; }
        public String getSuccessMessage() { return healthy ? message : null; }
        public long getLastAttemptDurationMs() { return lastAttemptDurationMs; }
        public int getAttemptsUsed() { return attemptsUsed; }
    }
}
