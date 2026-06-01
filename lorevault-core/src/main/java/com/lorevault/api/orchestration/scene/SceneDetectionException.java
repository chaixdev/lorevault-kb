package com.lorevault.api.orchestration.scene;

import com.lorevault.api.orchestration.job.IngestionFailure;
import com.lorevault.api.orchestration.job.IngestionFailureCarrier;

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
