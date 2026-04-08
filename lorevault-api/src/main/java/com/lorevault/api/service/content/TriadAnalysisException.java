package com.lorevault.api.service.content;

import com.lorevault.api.domain.ingestion.IngestionFailure;

/**
 * Domain exception for malformed or incomplete triad analysis output.
 */
public class TriadAnalysisException extends RuntimeException {

    private final IngestionFailure failure;

    public TriadAnalysisException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Triad analysis failed");
        this.failure = failure;
    }

    public IngestionFailure failure() {
        return failure;
    }
}
