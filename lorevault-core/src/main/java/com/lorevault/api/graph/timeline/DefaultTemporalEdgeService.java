package com.lorevault.api.graph.timeline;

import com.lorevault.api.graph.timeline.domain.CrossChapterBoundary;
import com.lorevault.api.graph.timeline.domain.CrossChapterBoundaryProjection;
import com.lorevault.api.graph.timeline.infrastructure.TemporalEdgeWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for creating structural reading-order adjacency edges between scenes in a book.
 * Creates NEXT_IN_READING_ORDER edges for consecutive scenes within and across chapters.
 * Provides idempotent operations that can be safely called multiple times.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTemporalEdgeService {
    
    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;
    
    /**
     * Create all default structural adjacency edges for a book
     * (both in-chapter and cross-chapter).
     * 
     * @param bookId the book to create edges for
     */
    @Transactional
    public DefaultTemporalEdgeCreationResult createAllDefaults(UUID bookId) {
        log.info("Creating default NEXT_IN_READING_ORDER edges for book {}", bookId);
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
        List<CrossChapterBoundary> newlyCreatedCrossChapterBoundaries =
                createCrossChapterDefault(bookId);
        int crossChapterEdges = newlyCreatedCrossChapterBoundaries.size();

        int totalEdges = inChapterEdges + crossChapterEdges;
        log.info("Created {} NEXT_IN_READING_ORDER edges for book {} ({} in-chapter, {} cross-chapter)", 
                totalEdges, bookId, inChapterEdges, crossChapterEdges);
        // Adapt CrossChapterBoundary records to CrossChapterBoundaryProjection for callers
        List<CrossChapterBoundaryProjection> boundaryProjections = 
                newlyCreatedCrossChapterBoundaries.stream().map(b -> (CrossChapterBoundaryProjection) b).toList();
        return new DefaultTemporalEdgeCreationResult(
                inChapterEdges,
                crossChapterEdges,
                boundaryProjections
        );
    }
    
    /**
     * Create structural adjacency edges within chapters (scene-to-scene within same chapter).
     * Safe to call multiple times - uses MERGE for idempotency.
     * Called internally by {@link #createAllDefaults(UUID)} — transaction is inherited from the caller.
     *
     * @param bookId the book to create in-chapter edges for
     * @return number of edges created
     */
    public int createInChapterDefaults(UUID bookId) {
        log.debug("Creating default in-chapter NEXT_IN_READING_ORDER edges for book {}", bookId);
        
        try {
            int edgeCount = temporalEdgeWriteRepository.mergeInChapterDefaultEdges(bookId);
            log.debug("Created {} in-chapter NEXT_IN_READING_ORDER edges for book {}", edgeCount, bookId);
            return edgeCount;
        } catch (Exception e) {
            log.warn("Failed to create in-chapter NEXT_IN_READING_ORDER edges for book {}: {}", 
                    bookId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Create structural adjacency edges between chapters
     * (last scene of chapter to first scene of next).
     * Safe to call multiple times - uses MERGE for idempotency.
     * Called internally by {@link #createAllDefaults(UUID)} — transaction is inherited from the caller.
     *
     * @param bookId the book to create cross-chapter edges for
     * @return list of newly created cross-chapter boundaries
     */
    public List<CrossChapterBoundary> createCrossChapterDefault(UUID bookId) {
        log.debug("Creating default cross-chapter NEXT_IN_READING_ORDER edges for book {}", bookId);
        
        try {
            List<CrossChapterBoundary> boundaries =
                    temporalEdgeWriteRepository.mergeCrossChapterDefaultEdges(bookId);
            log.debug("Created {} cross-chapter NEXT_IN_READING_ORDER edges for book {}", boundaries.size(), bookId);
            return boundaries;
        } catch (Exception e) {
            log.warn("Failed to create cross-chapter NEXT_IN_READING_ORDER edges for book {}: {}", 
                    bookId, e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Count existing NEXT_IN_READING_ORDER edges from scenes in a chapter (for testing).
     * 
     * @param chapterId the chapter to count edges for
     * @return number of NEXT_IN_READING_ORDER edges originating from scenes in this chapter
     */
    public int countTemporalEdgesFromChapter(UUID chapterId) {
        return temporalEdgeWriteRepository.countTemporalEdgesFromChapter(chapterId);
    }
}
