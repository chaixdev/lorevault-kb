package com.lorevault.api.ingestion.domain;

/**
 * Domain exception for malformed or incomplete triad analysis output.
 */
public class TriadAnalysisException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public TriadAnalysisException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Triad analysis failed");
        this.failure = failure;
    }

    public IngestionFailure failure() {
        return failure;
    }
}
