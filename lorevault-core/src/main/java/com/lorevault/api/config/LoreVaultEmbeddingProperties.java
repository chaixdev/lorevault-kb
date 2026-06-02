package com.lorevault.api.config;

/**
 * Embedding configuration constants.
 *
 * <p>The embedding dimension is a design-time decision tied to the chosen
 * embedding model. Changing it at runtime would break all vector indexes and
 * semantic search — it is not a tunable property.
 */
public final class LoreVaultEmbeddingProperties {
    /** Expected embedding vector dimension. Must match the deployed embedding model. */
    public static final int DIMENSIONS = 1536;

    private LoreVaultEmbeddingProperties() {}
}
