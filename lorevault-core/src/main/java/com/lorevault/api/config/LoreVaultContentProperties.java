package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for content processing operations.
 * Centralizes configuration for text chunking, processing, and content management.
 */
@ConfigurationProperties(prefix = "lorevault.content")
@Validated
public record LoreVaultContentProperties(
    @Valid @NotNull ChunkingProperties chunking
) {
    
    /**
     * Configuration for text chunking operations.
     */
    public record ChunkingProperties(
        Integer decisionThreshold,
        Integer targetSize,
        Integer overlapPercentage,
        Integer minChunkSize,
        Integer maxChunkSize
    ) {
        public ChunkingProperties {
            if (decisionThreshold == null) decisionThreshold = 1500;
            if (targetSize == null)         targetSize = 800;
            if (overlapPercentage == null)  overlapPercentage = 25;
            if (minChunkSize == null)       minChunkSize = 400;
            if (maxChunkSize == null)       maxChunkSize = 1200;
        }
    }
}
