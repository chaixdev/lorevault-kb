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
    /** Maximum attempts for remote embedding call (including the first). */
    private int maxAttempts = 3;
    /** Initial backoff delay in milliseconds. */
    private long initialDelayMillis = 200;
    /** Exponential backoff multiplier. */
    private double backoffMultiplier = 2.0;
    /** Maximum backoff delay cap in milliseconds. */
    private long maxDelayMillis = 2000;
}
