package com.lorevault.api.ai.embedding;

/**
 * Business exception for embedding-stage failures where the backend could not
 * produce vectors for requested chunk content.
 */
public class EmbeddingGenerationException extends RuntimeException {

    private final EmbeddingFailure failure;

    public EmbeddingGenerationException(EmbeddingFailure failure) {
        super(failure != null ? failure.message() : "Embedding generation failed");
        this.failure = failure;
    }

    public EmbeddingGenerationException(EmbeddingFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Embedding generation failed", cause);
        this.failure = failure;
    }

    public EmbeddingFailure failure() {
        return failure;
    }
}
