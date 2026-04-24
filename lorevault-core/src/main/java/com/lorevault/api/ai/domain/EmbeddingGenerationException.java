package com.lorevault.api.ai.domain;

import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionFailureCarrier;

/**
 * Business exception for embedding-stage failures where the backend could not
 * produce vectors for requested chunk content.
 */
public class EmbeddingGenerationException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public EmbeddingGenerationException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Embedding generation failed");
        this.failure = failure;
    }

    public EmbeddingGenerationException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Embedding generation failed", cause);
        this.failure = failure;
    }

    @Override
    public IngestionFailure failure() {
        return failure;
    }
}
