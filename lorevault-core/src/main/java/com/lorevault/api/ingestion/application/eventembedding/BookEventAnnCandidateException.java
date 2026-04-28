package com.lorevault.api.ingestion.application.eventembedding;

import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionFailureCarrier;

/**
 * Structured failure for Stage 4 ChapterEvent ANN candidate generation.
 */
public class BookEventAnnCandidateException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public BookEventAnnCandidateException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "ChapterEvent ANN candidate generation failed");
        this.failure = failure;
    }

    public BookEventAnnCandidateException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "ChapterEvent ANN candidate generation failed", cause);
        this.failure = failure;
    }

    @Override
    public IngestionFailure failure() {
        return failure;
    }
}
