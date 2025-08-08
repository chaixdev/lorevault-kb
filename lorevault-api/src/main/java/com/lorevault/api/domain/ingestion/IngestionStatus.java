package com.lorevault.api.domain.ingestion;

/**
 * Enumeration of possible states for an ingestion job.
 * Represents the progression through the content processing pipeline.
 */
public enum IngestionStatus {
    
    /**
     * The request has been accepted and is awaiting processing by a worker
     */
    QUEUED,
    
    /**
     * Job dequeued. Text normalization and content hash deduplication is in progress
     */
    PREPROCESSING_STARTED,
    
    /**
     * The local SLM is actively analyzing the text to identify semantic scene boundaries
     */
    DETECTING_SCENES,
    
    /**
     * The local SLM is performing its initial pass to extract all potential entity mentions
     */
    EXTRACTING_ENTITIES,
    
    /**
     * The system is creating technical chunks from the identified scenes and generating their vector embeddings
     */
    EMBEDDING_CHUNKS,
    
    /**
     * The RAG loop is active for character entities. The system is synthesizing structured data for characters
     */
    SYNTHESIZING_CHARACTERS,
    
    /**
     * The RAG loop is active for location entities
     */
    SYNTHESIZING_LOCATIONS,
    
    /**
     * The RAG loop is active for item entities
     */
    SYNTHESIZING_ITEMS,
    
    /**
     * All synthesis is complete. The system is performing final conflict resolution and saving enhanced entity data
     */
    PERSISTING_DATA,
    
    /**
     * All stages finished successfully. The ingested content is now available for querying
     */
    COMPLETE,
    
    /**
     * The process terminated due to an unrecoverable error. Details are logged in the final status record
     */
    FAILED;
    
    /**
     * Check if this status represents a terminal state (job has finished)
     */
    public boolean isTerminal() {
        return this == COMPLETE || this == FAILED;
    }
    
    /**
     * Get the estimated progress percentage for this status
     */
    public int getProgressPercentage() {
        return switch (this) {
            case QUEUED -> 0;
            case PREPROCESSING_STARTED -> 5;
            case DETECTING_SCENES -> 15;
            case EXTRACTING_ENTITIES -> 30;
            case EMBEDDING_CHUNKS -> 45;
            case SYNTHESIZING_CHARACTERS -> 60;
            case SYNTHESIZING_LOCATIONS -> 75;
            case SYNTHESIZING_ITEMS -> 85;
            case PERSISTING_DATA -> 95;
            case COMPLETE -> 100;
            case FAILED -> -1; // Indicates error state
        };
    }
}
