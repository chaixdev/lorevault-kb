package com.lorevault.api.ai;

import com.lorevault.api.ingestion.IngestionFailure;

/**
 * Business exception for expected scene-localization failures in the scene detection pipeline.
 */
public class SceneLocalizationException extends RuntimeException {

    private final IngestionFailure failure;

    public SceneLocalizationException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Scene localization failed");
        this.failure = failure;
    }

    public SceneLocalizationException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Scene localization failed", cause);
        this.failure = failure;
    }

    public IngestionFailure failure() {
        return failure;
    }
}
