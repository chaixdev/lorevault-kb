package com.lorevault.api.ingestion.job;

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
     * The system is resolving cross-scene event co-reference links for a chapter.
     */
    EVENT_COREF,

    /**
     * The system is aggregating co-reference chains into chapter-level event records.
     */
    CHAPTER_EVENT_AGGREGATION,

    /**
     * The system is embedding chapter-level events and generating book-event merge candidates.
     */
    EVENT_CANDIDATE_GENERATION,
    
    /**
     * The system is creating technical chunks from the identified scenes
     */
    CHUNKING,

    /**
     * The system is generating vector embeddings for the chunks
     */
    EMBEDDING_CHUNKS,
    
    /**
     * The system is resolving individual entity mentions into chapter-level individual records.
     */
    RESOLVING_INDIVIDUALS,

    /**
     * The system is resolving collective entity mentions into chapter-level collective records.
     */
    RESOLVING_COLLECTIVES,

    /**
     * The system is resolving location entity mentions into chapter-level location records.
     */
    RESOLVING_LOCATIONS,

    /**
     * The system is resolving object entity mentions into chapter-level object records.
     */
    RESOLVING_OBJECTS,

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
            case EVENT_COREF -> 42;
            case CHAPTER_EVENT_AGGREGATION -> 46;
            case EVENT_CANDIDATE_GENERATION -> 48;
            case EMBEDDING_CHUNKS -> 50;
            case CHUNKING -> 50;
            case RESOLVING_INDIVIDUALS -> 55;
            case RESOLVING_COLLECTIVES -> 60;
            case RESOLVING_LOCATIONS -> 65;
            case RESOLVING_OBJECTS -> 70;
            case PERSISTING_DATA -> 95;
            case COMPLETE -> 100;
            case FAILED -> -1; // Indicates error state
        };
    }
}
