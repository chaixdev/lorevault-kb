package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for embedding operations.
 */
@ConfigurationProperties(prefix = "lorevault.embedding")
@Validated
public record LoreVaultEmbeddingProperties(
    Integer dimensions
) {
    public LoreVaultEmbeddingProperties {
        if (dimensions == null) dimensions = 1536;
    }
}
