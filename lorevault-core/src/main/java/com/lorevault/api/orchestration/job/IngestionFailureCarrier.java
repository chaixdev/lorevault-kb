package com.lorevault.api.orchestration.job;

/**
 * Marker contract for exceptions that carry structured ingestion failure semantics.
 */
public interface IngestionFailureCarrier {

    IngestionFailure failure();
}
