package com.lorevault.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lorevault.embedding")
@Data
public class EmbeddingProperties {
    /** Expected embedding dimensionality (e.g., 3072). */
    private int dim = 3072;
    /** Batch size for embedding generation. */
    private int batchSize = 32;
}
