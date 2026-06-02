package com.lorevault.api.orchestration.submission;

import com.lorevault.api.orchestration.job.IngestionFailure;
import com.lorevault.api.orchestration.job.IngestionFailureCarrier;

/**
 * Business exception for lookup failures during chapter submission where
 * fallback behavior would risk duplicate work or hidden state errors.
 */
public class ChapterSubmissionLookupException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public ChapterSubmissionLookupException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Chapter submission lookup failed");
        this.failure = failure;
    }

    public ChapterSubmissionLookupException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Chapter submission lookup failed", cause);
        this.failure = failure;
    }

    @Override
    public IngestionFailure failure() {
        return failure;
    }
}
