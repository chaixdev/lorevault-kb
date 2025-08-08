package com.lorevault.api.repository;

import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for IngestionJob entities
 */
@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    /**
     * Find all jobs for a specific chapter
     */
    List<IngestionJob> findByChapterId(UUID chapterId);

    /**
     * Find jobs by current status
     */
    List<IngestionJob> findByCurrentStatus(IngestionStatus status);

    // --- Added pageable variants for listing (do not redeclare findAll(Pageable)) ---
    Page<IngestionJob> findByCurrentStatus(IngestionStatus status, Pageable pageable);
    Page<IngestionJob> findByCurrentStatusNotIn(List<IngestionStatus> statuses, Pageable pageable);
    Page<IngestionJob> findByChapterIdIn(List<UUID> chapterIds, Pageable pageable);
    Page<IngestionJob> findByChapterIdInAndCurrentStatus(List<UUID> chapterIds, IngestionStatus status, Pageable pageable);
    Page<IngestionJob> findByChapterIdInAndCurrentStatusNotIn(List<UUID> chapterIds, List<IngestionStatus> statuses, Pageable pageable);

    /**
     * Find the most recent job for a chapter
     */
    @Query("SELECT j FROM IngestionJob j WHERE j.chapterId = :chapterId ORDER BY j.createdAt DESC")
    Optional<IngestionJob> findMostRecentByChapterId(@Param("chapterId") UUID chapterId);

    /**
     * Check if there's an active (non-terminal) job for a chapter
     */
    @Query("SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END FROM IngestionJob j " +
           "WHERE j.chapterId = :chapterId AND j.currentStatus NOT IN ('COMPLETE', 'FAILED')")
    boolean hasActiveJobForChapter(@Param("chapterId") UUID chapterId);
}
