package com.lorevault.api.orchestration.job;

/**
 * Enumeration of possible states for an ingestion job.
 * Represents the progression through the content processing pipeline.
 */
public enum IngestionStatus {
    
    /**
     * Chapter segmentation: Scene boundary detection and segmentation
     */
    SCENE_SEGMENTATION,
    
    /**
     * Scene analysis: Triad-based analysis (prev/curr/next) for temporal relations
     */
    SCENE_TRIAD_ANALYSIS,

    /**
     * The system is resolving cross-scene event co-reference links for a chapter.
     */
    EVENT_COREF,

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
     * The system is resolving concept entity mentions into chapter-level concept records.
     */
    RESOLVING_CONCEPTS,
    
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
            case SCENE_SEGMENTATION -> 15;
            case SCENE_TRIAD_ANALYSIS -> 25;
            case EVENT_COREF -> 42;
            case EVENT_CANDIDATE_GENERATION -> 48;
            case EMBEDDING_CHUNKS -> 50;
            case CHUNKING -> 50;
            case RESOLVING_INDIVIDUALS -> 55;
            case RESOLVING_COLLECTIVES -> 60;
            case RESOLVING_LOCATIONS -> 65;
            case RESOLVING_OBJECTS -> 70;
            case RESOLVING_CONCEPTS -> 75;
            case COMPLETE -> 100;
            case FAILED -> -1; // Indicates error state
        };
    }
}
