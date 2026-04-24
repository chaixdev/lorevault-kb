package com.lorevault.api.ingestion.domain;

/**
 * Marker contract for exceptions that carry structured ingestion failure semantics.
 */
public interface IngestionFailureCarrier {

    IngestionFailure failure();
}
