package com.lorevault.api.service.timeline;

import com.lorevault.api.infrastructure.persistence.neo4j.repository.TemporalEdgeWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for creating default temporal edges between scenes in a book.
 * Implements the skeleton timeline approach from LV-084-1 by creating
 * MEETS@HEURISTIC edges for consecutive scenes within and across chapters.
 * Provides idempotent operations that can be safely called multiple times.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTemporalEdgeService {
    
    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;
    
    /**
     * Create all default temporal edges for a book (both in-chapter and cross-chapter).
     * This is the main entry point for setting up temporal relationships.
     * 
     * @param bookId the book to create edges for
     */
    @Transactional
    public void createAllDefaults(UUID bookId) {
        log.info("Creating default temporal edges for book {}", bookId);
        // Pre-flight: log potential cycle candidates for observability
        try {
            int inChapterCycleCandidates = temporalEdgeWriteRepository.countInChapterCycleCandidates(bookId);
            int crossChapterCycleCandidates = temporalEdgeWriteRepository.countCrossChapterCycleCandidates(bookId);
            if (inChapterCycleCandidates > 0 || crossChapterCycleCandidates > 0) {
                log.warn("Detected potential cycle-inducing candidates before creation (book {}): in-chapter={}, cross-chapter={}",
                        bookId, inChapterCycleCandidates, crossChapterCycleCandidates);
            } else {
                log.debug("No potential cycle-inducing candidates detected before creation for book {}", bookId);
            }
        } catch (Exception e) {
            log.debug("Cycle candidate pre-check failed for book {}: {}", bookId, e.getMessage());
        }

        int inChapterEdges = createInChapterDefaults(bookId);
        int crossChapterEdges = createCrossChapterDefault(bookId);
        
        int totalEdges = inChapterEdges + crossChapterEdges;
        log.info("Created {} default temporal edges for book {} ({} in-chapter, {} cross-chapter)", 
                totalEdges, bookId, inChapterEdges, crossChapterEdges);
    }
    
    /**
     * Create default temporal edges within chapters (scene-to-scene within same chapter).
     * Links consecutive scenes with MEETS@HEURISTIC edges.
     * Safe to call multiple times - uses MERGE for idempotency.
     * 
     * @param bookId the book to create in-chapter edges for
     * @return number of edges created
     */
    @Transactional
    public int createInChapterDefaults(UUID bookId) {
        log.debug("Creating default in-chapter temporal edges for book {}", bookId);
        
        try {
            int edgeCount = temporalEdgeWriteRepository.mergeInChapterDefaultEdges(bookId);
            log.debug("Created {} default temporal edges within chapters of book {}", edgeCount, bookId);
            return edgeCount;
        } catch (Exception e) {
            log.warn("Failed to create in-chapter temporal edges for book {}: {}", 
                    bookId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Create default temporal edges between chapters (last scene of chapter to first scene of next).
     * Establishes cross-chapter continuity in the timeline.
     * Safe to call multiple times - uses MERGE for idempotency.
     * 
     * @param bookId the book to create cross-chapter edges for  
     * @return number of edges created
     */
    @Transactional
    public int createCrossChapterDefault(UUID bookId) {
        log.debug("Creating default cross-chapter temporal edges for book {}", bookId);
        
        try {
            int edgeCount = temporalEdgeWriteRepository.mergeCrossChapterDefaultEdge(bookId);
            log.debug("Created {} cross-chapter temporal edges for book {}", edgeCount, bookId);
            return edgeCount;
        } catch (Exception e) {
            log.warn("Failed to create cross-chapter temporal edges for book {}: {}", 
                    bookId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Count existing temporal edges from scenes in a chapter (for testing).
     * 
     * @param chapterId the chapter to count edges for
     * @return number of temporal edges originating from scenes in this chapter
     */
    public int countTemporalEdgesFromChapter(UUID chapterId) {
        return temporalEdgeWriteRepository.countTemporalEdgesFromChapter(chapterId);
    }
}
