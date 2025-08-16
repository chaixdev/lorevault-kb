package com.lorevault.api.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Multi-provider LLM configuration properties.
 * Supports different LLM providers (Groq, Gemini, etc.) for different tasks.
 * All providers must use OpenAI-compatible APIs.
 */
@ConfigurationProperties(prefix = "lorevault.multi-llm")
@Validated
public record LoreVaultMultiLlmProperties(
    @Valid @NotNull Map<String, ProviderProperties> providers,
    @Valid @NotNull TaskMappingProperties tasks,
    @Valid @NotNull RetryProperties retry,
    @Valid @NotNull PromptsProperties prompts
) {
    
    /**
     * Configuration for a specific LLM provider.
     */
    public record ProviderProperties(
        @NotNull @NotBlank String name,
        @NotNull @NotBlank String baseUrl,
        @NotNull @NotBlank String apiKey,
        @NotNull @NotBlank String chatModel,
        String embeddingModel, // Optional - not all providers support embeddings
        @NotNull @Positive Double temperature,
        @NotNull @Positive Double topP,
        @NotNull @Positive Integer maxTokens,
        Boolean enabled
    ) {
        public ProviderProperties {
            if (enabled == null) {
                enabled = true;
            }
        }
        
        /**
         * Check if this provider supports embeddings.
         */
        public boolean supportsEmbeddings() {
            return embeddingModel != null && !embeddingModel.trim().isEmpty();
        }
    }
    
    /**
     * Mapping of domain-specific tasks to their preferred and fallback providers.
     */
    public record TaskMappingProperties(
        @Valid @NotNull TaskProviderMapping embeddings,
        @Valid @NotNull TaskProviderMapping extractScenes,
        @Valid TaskProviderMapping extractEntities, // Optional - future feature
        @Valid TaskProviderMapping generateResponse // Optional - future feature
    ) {}
    
    /**
     * Provider mapping for a specific task.
     */
    public record TaskProviderMapping(
        @NotNull @NotBlank String primary,
        String fallback // Optional fallback provider
    ) {}
    
    /**
     * LLM retry configuration with exponential backoff.
     */
    public record RetryProperties(
        @NotNull @Positive Integer maxAttempts,
        @NotNull @Positive Long baseDelayMs,
        @NotNull @Positive Double backoffMultiplier,
        @NotNull @Positive Long maxDelayMs,
        @NotNull @Positive Double jitterFactor
    ) {}
    
    /**
     * Configuration for LLM prompt templates.
     */
    public record PromptsProperties(
        @NotNull @NotBlank String basePath,
        @NotNull @NotBlank String sceneDetection
    ) {
        /**
         * Get the full resource path for scene detection prompt.
         */
        public String getSceneDetectionPath() {
            return basePath + "/" + sceneDetection;
        }
    }
    
    /**
     * Get provider configuration by name.
     */
    public ProviderProperties getProvider(String name) {
        return providers.get(name);
    }
    
    /**
     * Get the primary provider for scene extraction tasks.
     */
    public ProviderProperties getPrimarySceneExtractionProvider() {
        return getProvider(tasks.extractScenes().primary());
    }
    
    /**
     * Get the fallback provider for scene extraction tasks.
     */
    public ProviderProperties getFallbackSceneExtractionProvider() {
        String fallback = tasks.extractScenes().fallback();
        return fallback != null ? getProvider(fallback) : null;
    }
    
    /**
     * Get the primary provider for embedding tasks.
     */
    public ProviderProperties getPrimaryEmbeddingProvider() {
        return getProvider(tasks.embeddings().primary());
    }
    
    /**
     * Get the fallback provider for embedding tasks.
     */
    public ProviderProperties getFallbackEmbeddingProvider() {
        String fallback = tasks.embeddings().fallback();
        return fallback != null ? getProvider(fallback) : null;
    }
}