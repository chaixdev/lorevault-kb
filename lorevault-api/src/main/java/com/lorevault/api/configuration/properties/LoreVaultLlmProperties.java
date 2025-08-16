package com.lorevault.api.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for all LoreVault LLM integrations.
 * Provides centralized configuration for scene detection, retry policies,
 * and future multi-provider capabilities.
 */
@ConfigurationProperties(prefix = "lorevault.llm")
@Validated
public record LoreVaultLlmProperties(
    @Valid @NotNull SceneDetectionProperties sceneDetection,
    @Valid @NotNull RetryProperties retry,
    @Valid @NotNull PromptsProperties prompts
) {
    
    /**
     * Configuration for scene detection LLM operations.
     */
    public record SceneDetectionProperties(
        String provider,
        OpenAiProperties openai
    ) {
        public SceneDetectionProperties {
            // Default to OpenAI if no provider specified
            if (provider == null) {
                provider = "openai";
            }
        }
    }
    
    /**
     * OpenAI specific configuration for scene detection.
     */
    public record OpenAiProperties(
        String model,
        Double temperature,
        Integer maxTokens,
        String baseUrl,
        String apiKey
    ) {
        public OpenAiProperties {
            // Apply defaults if not specified
            if (model == null) {
                model = "gpt-4o-mini";
            }
            if (temperature == null) {
                temperature = 0.1;
            }
            if (maxTokens == null) {
                maxTokens = 4000;
            }
            if (baseUrl == null) {
                baseUrl = "https://api.openai.com/v1";
            }
        }
    }
    
    /**
     * LLM retry configuration with exponential backoff.
     */
    public record RetryProperties(
        Integer maxAttempts,
        Long baseDelayMs,
        Double backoffMultiplier,
        Long maxDelayMs,
        Double jitterFactor
    ) {
        public RetryProperties {
            // Apply sensible defaults
            if (maxAttempts == null) {
                maxAttempts = 3;
            }
            if (baseDelayMs == null) {
                baseDelayMs = 1000L;
            }
            if (backoffMultiplier == null) {
                backoffMultiplier = 2.0;
            }
            if (maxDelayMs == null) {
                maxDelayMs = 10000L;
            }
            if (jitterFactor == null) {
                jitterFactor = 0.1;
            }
        }
    }
    
    /**
     * Configuration for LLM prompt templates.
     */
    public record PromptsProperties(
        String basePath,
        String sceneDetection
    ) {
        public PromptsProperties {
            // Apply defaults
            if (basePath == null) {
                basePath = "classpath:prompts";
            }
            if (sceneDetection == null) {
                sceneDetection = "scene-detection-v2.txt";
            }
        }
        
        /**
         * Get the full resource path for scene detection prompt.
         */
        public String getSceneDetectionPath() {
            return basePath + "/" + sceneDetection;
        }
    }
}
