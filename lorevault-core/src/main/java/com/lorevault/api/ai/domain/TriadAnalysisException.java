package com.lorevault.api.ai.domain;

import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionFailureCarrier;

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
