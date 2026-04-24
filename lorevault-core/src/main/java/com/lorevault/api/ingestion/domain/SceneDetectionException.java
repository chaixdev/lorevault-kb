package com.lorevault.api.ingestion.domain;

/**
 * Business exception for scene-detection stage failures that should preserve
 * structured ingestion failure semantics.
 */
public class SceneDetectionException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public SceneDetectionException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Scene detection failed");
        this.failure = failure;
    }

    public SceneDetectionException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Scene detection failed", cause);
        this.failure = failure;
    }

    public IngestionFailure failure() {
        return failure;
    }
}
