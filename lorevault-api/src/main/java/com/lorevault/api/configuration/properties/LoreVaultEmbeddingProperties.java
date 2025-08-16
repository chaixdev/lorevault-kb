package com.lorevault.api.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for embedding operations.
 * Centralizes configuration for text embeddings and vector operations.
 */
@ConfigurationProperties(prefix = "lorevault.embedding")
@Validated
public record LoreVaultEmbeddingProperties(
    @Valid @NotNull ModelProperties model,
    @Valid @NotNull ProcessingProperties processing
) {
    
    /**
     * Configuration for embedding model settings.
     */
    public record ModelProperties(
        String provider,
        String model,
        Integer dimensions,
        Integer batchSize
    ) {
        public ModelProperties {
            // Apply defaults to match existing EmbeddingProperties
            if (provider == null) {
                provider = "openai";
            }
            if (model == null) {
                model = "text-embedding-3-small";
            }
            if (dimensions == null) {
                dimensions = 3072; // Match existing default
            }
            if (batchSize == null) {
                batchSize = 32; // Match existing default
            }
        }
    }
    
    /**
     * Configuration for embedding processing operations.
     * Includes retry settings that were previously in EmbeddingProperties.
     */
    public record ProcessingProperties(
        Boolean parallel,
        Integer maxConcurrency,
        Long timeoutMs,
        // Retry settings from old EmbeddingProperties
        Integer maxAttempts,
        Long initialDelayMillis,
        Double backoffMultiplier,
        Long maxDelayMillis
    ) {
        public ProcessingProperties {
            // Apply defaults
            if (parallel == null) {
                parallel = true;
            }
            if (maxConcurrency == null) {
                maxConcurrency = 5;
            }
            if (timeoutMs == null) {
                timeoutMs = 30000L;
            }
            // Retry defaults matching old EmbeddingProperties
            if (maxAttempts == null) {
                maxAttempts = 3;
            }
            if (initialDelayMillis == null) {
                initialDelayMillis = 200L;
            }
            if (backoffMultiplier == null) {
                backoffMultiplier = 2.0;
            }
            if (maxDelayMillis == null) {
                maxDelayMillis = 2000L;
            }
        }
    }
}
