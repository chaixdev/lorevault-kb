package com.lorevault.api.ai.domain;

import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionFailureCarrier;

/**
 * Business exception for expected scene-localization failures in the scene detection pipeline.
 */
public class SceneLocalizationException extends RuntimeException implements IngestionFailureCarrier {

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
