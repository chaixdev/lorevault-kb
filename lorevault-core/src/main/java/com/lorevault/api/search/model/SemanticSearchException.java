package com.lorevault.api.search.model;

import com.lorevault.api.orchestration.job.IngestionFailure;
import com.lorevault.api.orchestration.job.IngestionFailureCarrier;

/**
 * Business exception for semantic-search backend failures that should preserve
 * structured failure semantics instead of collapsing to "no results".
 */
public class SemanticSearchException extends RuntimeException implements IngestionFailureCarrier {

    private final IngestionFailure failure;

    public SemanticSearchException(IngestionFailure failure) {
        super(failure != null ? failure.message() : "Semantic search failed");
        this.failure = failure;
    }

    public SemanticSearchException(IngestionFailure failure, Throwable cause) {
        super(failure != null ? failure.message() : "Semantic search failed", cause);
        this.failure = failure;
    }

    @Override
    public IngestionFailure failure() {
        return failure;
    }
}
