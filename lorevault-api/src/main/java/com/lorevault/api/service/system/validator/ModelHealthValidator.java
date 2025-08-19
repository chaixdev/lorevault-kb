package com.lorevault.api.service.system.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Core health validation logic for LLM models.
 * Handles the actual health check communication with the model.
 * Extracted from LlmHealthCheckService to improve single responsibility and testability.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModelHealthValidator {

    private final ChatClient chatClient;

    /**
     * Result of a health validation check
     */
    public static class HealthCheckResult {
        private final boolean valid;
        private final String response;
        private final String errorMessage;

        private HealthCheckResult(boolean valid, String response, String errorMessage) {
            this.valid = valid;
            this.response = response;
            this.errorMessage = errorMessage;
        }

        public static HealthCheckResult success(String response) {
            return new HealthCheckResult(true, response, null);
        }

        public static HealthCheckResult failure(String errorMessage) {
            return new HealthCheckResult(false, null, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getResponse() { return response; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Configuration for health check behavior
     */
    public static class ValidationConfig {
        private final String healthCheckPrompt;
        private final boolean requireNonEmptyResponse;
        private final boolean logRawResponse;

        public ValidationConfig(String healthCheckPrompt, boolean requireNonEmptyResponse, boolean logRawResponse) {
            this.healthCheckPrompt = healthCheckPrompt;
            this.requireNonEmptyResponse = requireNonEmptyResponse;
            this.logRawResponse = logRawResponse;
        }

        public static ValidationConfig defaultConfig() {
            return new ValidationConfig(
                "Respond with 'OK' if you can process this message.", 
                true, 
                false
            );
        }

        public String getHealthCheckPrompt() { return healthCheckPrompt; }
        public boolean isRequireNonEmptyResponse() { return requireNonEmptyResponse; }
        public boolean isLogRawResponse() { return logRawResponse; }
    }

    /**
     * Perform a health validation check against the model
     * Throws exception on failure to work with retry mechanism
     */
    public HealthCheckResult validateModelHealth(String modelId, ValidationConfig config) throws Exception {
        log.trace("[Health-Validator] Sending health check to model: {}", modelId);
        Prompt healthCheckPrompt = new Prompt(config.getHealthCheckPrompt());
        String response = chatClient.prompt(healthCheckPrompt).call().content();
        
        if (config.isLogRawResponse()) {
            log.trace("[Health-Validator] Raw response from model {}: {}", modelId, response);
        }
        
        // Validate response according to configuration
        if (config.isRequireNonEmptyResponse() && (response == null || response.trim().isEmpty())) {
            throw new RuntimeException("Empty or null response received from model");
        }
        
        // Additional validation can be added here
        String trimmedResponse = response != null ? response.trim() : null;
        return HealthCheckResult.success(trimmedResponse);
    }

    /**
     * Variant that allows specifying which ChatClient to use (e.g., small vs big slot).
     */
    public HealthCheckResult validateModelHealthWithClient(ChatClient client, String modelId, ValidationConfig config) throws Exception {
        log.trace("[Health-Validator] (with-client) Sending health check to model: {}", modelId);
        Prompt healthCheckPrompt = new Prompt(config.getHealthCheckPrompt());
        String response = client.prompt(healthCheckPrompt).call().content();
        if (config.isLogRawResponse()) {
            log.trace("[Health-Validator] Raw response from model {}: {}", modelId, response);
        }
        if (config.isRequireNonEmptyResponse() && (response == null || response.trim().isEmpty())) {
            throw new RuntimeException("Empty or null response received from model");
        }
        String trimmedResponse = response != null ? response.trim() : null;
        return HealthCheckResult.success(trimmedResponse);
    }

    /**
     * Perform a basic connectivity test (simplified health check)
     * Throws exception on failure to work with retry mechanism
     */
    public HealthCheckResult performConnectivityTest(String modelId) throws Exception {
        return validateModelHealth(modelId, ValidationConfig.defaultConfig());
    }

    /** Perform connectivity test with a specified ChatClient. */
    public HealthCheckResult performConnectivityTestWith(ChatClient client, String modelId) throws Exception {
        return validateModelHealthWithClient(client, modelId, ValidationConfig.defaultConfig());
    }

    /**
     * Perform a custom health check with specific prompt
     * Throws exception on failure to work with retry mechanism
     */
    public HealthCheckResult performCustomHealthCheck(String modelId, String customPrompt) throws Exception {
        ValidationConfig customConfig = new ValidationConfig(customPrompt, true, true);
        return validateModelHealth(modelId, customConfig);
    }
}
