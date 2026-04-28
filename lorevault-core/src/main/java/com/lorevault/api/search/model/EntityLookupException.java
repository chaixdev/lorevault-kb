package com.lorevault.api.search.model;

import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.job.IngestionFailureCarrier;

/**
 * Business exception for entity-lookup query failures in the search layer.
 */
public class EntityLookupException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public EntityLookupException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Entity lookup failed");
        this.failure = failure;
    }

    public EntityLookupException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Entity lookup failed", cause);
        this.failure = failure;
    }

    @Override
    public IngestionFailure failure() {
        return failure;
    }
}
