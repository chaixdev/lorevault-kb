package com.lorevault.api.graph.event.consolidation.book;

import com.lorevault.api.orchestration.job.IngestionFailure;
import com.lorevault.api.orchestration.job.IngestionFailureCarrier;

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
