package com.lorevault.api.application.port;

import java.util.UUID;

/**
 * Port for creating default temporal edges between scenes.
 * Provides idempotent operations for building temporal relationships.
 */
public interface TemporalEdgePort {
    
    /**
     * Create default in-chapter temporal edges for all scenes within chapters of the given book.
     * Uses MERGE operations to ensure idempotency.
     * 
     * @param bookId the book ID to create edges for
     * @return number of edges created
     */
    int createInChapterDefaults(UUID bookId);
    
    /**
     * Create default cross-chapter temporal edges for the given book.
     * Links the last scene of each chapter to the first scene of the next chapter.
     * Uses MERGE operations to ensure idempotency.
     * 
     * @param bookId the book ID to create edges for  
     * @return number of edges created
     */
    int createCrossChapterDefault(UUID bookId);
    
    /**
     * Count existing temporal edges originating from scenes in the given chapter.
     * 
     * @param chapterId the chapter ID to count edges for
     * @return number of temporal edges from scenes in this chapter
     */
    int countTemporalEdgesFromChapter(UUID chapterId);
}