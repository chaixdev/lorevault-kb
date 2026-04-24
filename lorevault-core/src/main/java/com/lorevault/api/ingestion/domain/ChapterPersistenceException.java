package com.lorevault.api.ingestion.domain;

/**
 * Business exception for chapter-persistence failures during ingestion submission.
 */
public class ChapterPersistenceException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public ChapterPersistenceException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Chapter persistence failed");
        this.failure = failure;
    }

    public ChapterPersistenceException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Chapter persistence failed", cause);
        this.failure = failure;
    }

    @Override
    public IngestionFailure failure() {
        return failure;
    }
}
