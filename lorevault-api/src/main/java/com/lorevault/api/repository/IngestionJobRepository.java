package com.lorevault.api.repository;

import com.lorevault.api.model.IngestionJob;
import com.lorevault.api.model.IngestionStatus;
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
