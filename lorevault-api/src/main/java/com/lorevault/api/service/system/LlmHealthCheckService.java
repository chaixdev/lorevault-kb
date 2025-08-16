package com.lorevault.api.service.system;

import com.lorevault.api.service.system.metrics.HealthMetricsCollector;
import com.lorevault.api.service.system.retry.RetryableHealthChecker;
import com.lorevault.api.service.system.validator.ModelHealthValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Delegates retry logic, validation, and metrics collection to focused services.
 * Refactored to improve single responsibility and testability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmHealthCheckService {

    private final RetryableHealthChecker retryableHealthChecker;
    private final ModelHealthValidator modelHealthValidator;
    private final HealthMetricsCollector healthMetricsCollector;
    
    @Value("${lorevault.ai.models.nlp-big.model:unknown}")
    private String modelId;

    @Value("${lorevault.llm.health.enabled:true}")
    private boolean healthEnabled;

    /**
     * Performs health check on the configured LLM model after application startup.
     * Delegates to extracted services for retry logic, validation, and metrics collection.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void performStartupHealthCheck() {
        if (!healthEnabled) {
            log.info("LLM health check disabled via lorevault.llm.health.enabled=false");
            return;
        }
        
        Instant overallStart = Instant.now();
        log.info("Performing LLM service health check for model: {}", modelId);
        
        HealthMetricsCollector.ModelHealthStatus healthResult = checkCurrentModel();
        long totalMs = Duration.between(overallStart, Instant.now()).toMillis();
        
        if (healthResult.isHealthy()) {
            log.info("✅ LLM model '{}' is healthy ({} ms total)", modelId, totalMs);
        } else {
            log.error("❌ LLM model '{}' health check FAILED after {} ms: {}", 
                     modelId, totalMs, healthResult.getErrorMessage());
        }
    }
    
    /**
     * Checks the health of the currently configured model.
     * Uses extracted services for retry logic, validation, and metrics collection.
     * 
     * @return Health status of the current model
     */
    public HealthMetricsCollector.ModelHealthStatus checkCurrentModel() {
        return checkSingleModel(modelId);
    }
    
    /**
     * Checks the health of all configured models (for backwards compatibility).
     * Since we now only have one model, this returns a map with that single model.
     * 
     * @return Map of model names to their health status
     */
    public Map<String, HealthMetricsCollector.ModelHealthStatus> checkAllModels() {
        Map<String, HealthMetricsCollector.ModelHealthStatus> results = new LinkedHashMap<>();
        results.put(modelId, checkCurrentModel());
        return results;
    }
    
    /**
     * Checks the health of a specific model using extracted services.
     * Combines retry logic, validation, and metrics collection.
     * 
     * @param modelId The model identifier (e.g., "gemini-2.5-flash-lite")
     * @return Health status for the model
     */
    public HealthMetricsCollector.ModelHealthStatus checkSingleModel(String modelId) {
        String operationName = "health-check-" + modelId;
        RetryableHealthChecker.RetryConfig retryConfig = RetryableHealthChecker.RetryConfig.defaultConfig();
        
        // Use retry checker to execute health validation with backoff
        RetryableHealthChecker.RetryResult<ModelHealthValidator.HealthCheckResult> retryResult = 
            retryableHealthChecker.executeWithRetry(operationName, retryConfig, () -> {
                try {
                    return modelHealthValidator.performConnectivityTest(modelId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        
        // Convert retry result to health status using metrics collector
        if (retryResult.isSuccess()) {
            ModelHealthValidator.HealthCheckResult validationResult = retryResult.getResult();
            return healthMetricsCollector.recordSuccess(
                modelId, 
                validationResult.getResponse(),
                retryResult.getLastAttemptDurationMs(),
                retryResult.getTotalDurationMs(),
                retryResult.getAttemptsUsed()
            );
        } else {
            String errorMessage = retryResult.getLastException() != null 
                ? retryResult.getLastException().getMessage() 
                : "Unknown error";
            return healthMetricsCollector.recordFailure(
                modelId,
                errorMessage,
                retryResult.getLastAttemptDurationMs(),
                retryResult.getTotalDurationMs(),
                retryResult.getAttemptsUsed()
            );
        }
    }
    
    /**
     * Performs an on-demand health check of the configured LLM service.
     * Uses metrics collector to check current health status.
     * 
     * @return true if the service is healthy, false otherwise
     */
    public boolean isLlmServiceHealthy() {
        if (!healthEnabled) {
            return true; // treat disabled as healthy to not fail readiness
        }
        
        HealthMetricsCollector.ModelHealthStatus status = checkCurrentModel();
        return status.isHealthy();
    }

    /**
     * Get detailed metrics for the current model
     */
    public HealthMetricsCollector.ModelMetrics getCurrentModelMetrics() {
        return healthMetricsCollector.getModelMetrics(modelId);
    }

    /**
     * Get the last health status for the current model
     */
    public HealthMetricsCollector.ModelHealthStatus getLastHealthStatus() {
        return healthMetricsCollector.getLastStatus(modelId);
    }

    /**
     * @deprecated Use HealthMetricsCollector.ModelHealthStatus instead
     * Maintained for backwards compatibility
     */
    @Deprecated
    public static class ModelHealthStatus {
        private final HealthMetricsCollector.ModelHealthStatus delegate;

        public ModelHealthStatus(boolean healthy, String modelName, String message, 
                               long lastAttemptDurationMs, int attemptsUsed) {
            // Convert to new format with total duration = last attempt duration for compatibility
            this.delegate = new HealthMetricsCollector.ModelHealthStatus(
                healthy, modelName, message, lastAttemptDurationMs, lastAttemptDurationMs, attemptsUsed);
        }

        public boolean isHealthy() { return delegate.isHealthy(); }
        public String getModelName() { return delegate.getModelName(); }
        public String getErrorMessage() { return delegate.getErrorMessage(); }
        public String getSuccessMessage() { return delegate.getSuccessMessage(); }
        public long getLastAttemptDurationMs() { return delegate.getLastAttemptDurationMs(); }
        public int getAttemptsUsed() { return delegate.getAttemptsUsed(); }
    }
}
