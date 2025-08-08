package com.lorevault.api.service.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

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

    /**
     * Performs health check on the configured LLM model after application startup.
     * Fails early if the service is not accessible.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void performStartupHealthCheck() {
        log.info("Performing LLM service health check for model: {}", currentModelId);
        
        ModelHealthStatus healthResult = checkCurrentModel();
        
        if (healthResult.isHealthy()) {
            log.info("✅ LLM model '{}' is healthy", currentModelId);
        } else {
            log.error("❌ LLM model '{}' health check FAILED: {}", currentModelId, healthResult.getErrorMessage());
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
     * 
     * @param modelId The model identifier (e.g., "gemini-2.5-flash-lite")
     * @return Health status for the model
     */
    public ModelHealthStatus checkSingleModel(String modelId) {
        // Retry configuration for robustness during health checks
        int maxRetries = 3;
        long baseDelayMs = 1000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Checking health of model: {} - attempt {}/{}", modelId, attempt, maxRetries);
                
                // Create a simple health check prompt
                Prompt healthCheckPrompt = new Prompt("Respond with 'OK' if you can process this message.");
                
                // Make the call (the model is already configured in the ChatClient)
                String response = chatClient.prompt(healthCheckPrompt)
                        .call()
                        .content();
                
                // Log full response at trace level for debugging
                log.trace("Full LLM API response for health check (attempt {}): {}", attempt, response);
                
                if (response != null && !response.trim().isEmpty()) {
                    log.debug("✅ Model '{}' health check PASSED on attempt {}", modelId, attempt);
                    return new ModelHealthStatus(true, modelId, response.trim());
                } else {
                    String error = "Empty response received";
                    throw new RuntimeException(error);
                }
                
            } catch (Exception e) {
                log.warn("Health check attempt {}/{} failed for model '{}': {}", attempt, maxRetries, modelId, e.getMessage());
                
                if (attempt == maxRetries) {
                    String error = e.getMessage();
                    log.debug("❌ Model '{}' health check FAILED after {} attempts: {}", modelId, maxRetries, error);
                    return new ModelHealthStatus(false, modelId, error);
                }
                
                // Exponential backoff delay before retry
                long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1);
                try {
                    log.debug("Waiting {}ms before retry attempt {} for model '{}'", delayMs, attempt + 1, modelId);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Health check interrupted for model '{}'", modelId);
                    return new ModelHealthStatus(false, modelId, "Health check interrupted");
                }
            }
        }
        
        // This should never be reached due to the return in the catch block
        return new ModelHealthStatus(false, modelId, "Unexpected end of retry loop");
    }
    
    /**
     * Performs an on-demand health check of the configured LLM service.
     * 
     * @return true if the service is healthy, false otherwise
     */
    public boolean isLlmServiceHealthy() {
        ModelHealthStatus status = checkCurrentModel();
        return status.isHealthy();
    }
    
    /**
     * Data class to hold the health status of a specific model.
     */
    public static class ModelHealthStatus {
        private final boolean healthy;
        private final String modelName;
        private final String message;
        
        public ModelHealthStatus(boolean healthy, String modelName, String message) {
            this.healthy = healthy;
            this.modelName = modelName;
            this.message = message;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public String getModelName() {
            return modelName;
        }
        
        public String getErrorMessage() {
            return healthy ? null : message;
        }
        
        public String getSuccessMessage() {
            return healthy ? message : null;
        }
    }
}
