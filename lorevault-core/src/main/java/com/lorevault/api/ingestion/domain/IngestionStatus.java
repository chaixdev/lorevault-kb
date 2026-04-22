package com.lorevault.api.ingestion.domain;

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
     * @deprecated Use SCENE_SEGMENTATION for new code. Kept for backward compatibility.
     */
    @Deprecated
    DETECTING_SCENES,
    
    /**
     * Chapter segmentation: Scene boundary detection and segmentation
     */
    SCENE_SEGMENTATION,
    
    /**
     * Scene analysis: Triad-based analysis (prev/curr/next) for temporal relations
     */
    SCENE_TRIAD_ANALYSIS,
    
    /**
     * The local SLM is performing its initial pass to extract all potential entity mentions
     */
    EXTRACTING_ENTITIES,
    
    /**
     * The system is creating technical chunks from the identified scenes and generating their vector embeddings
     */
    EMBEDDING_CHUNKS,
    
    // /**
    //  * The RAG loop is active for character entities. The system is synthesizing structured data for characters
    //  */
    // SYNTHESIZING_CHARACTERS,
    
    // /**
    //  * The RAG loop is active for location entities
    //  */
    // SYNTHESIZING_LOCATIONS,
    
    // /**
    //  * The RAG loop is active for item entities
    //  */
    // SYNTHESIZING_ITEMS,
    
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
            case DETECTING_SCENES -> 15; // Keep for backward compatibility
            case SCENE_SEGMENTATION -> 15;
            case SCENE_TRIAD_ANALYSIS -> 25;
            case EXTRACTING_ENTITIES -> 35;
            case EMBEDDING_CHUNKS -> 50;
            // case SYNTHESIZING_CHARACTERS -> 60;
            // case SYNTHESIZING_LOCATIONS -> 75;
            // case SYNTHESIZING_ITEMS -> 85;
            case PERSISTING_DATA -> 95;
            case COMPLETE -> 100;
            case FAILED -> -1; // Indicates error state
        };
    }
}
