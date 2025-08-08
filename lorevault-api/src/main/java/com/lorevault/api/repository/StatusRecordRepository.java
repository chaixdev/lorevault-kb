package com.lorevault.api.repository;

import com.lorevault.api.domain.ingestion.StatusRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for StatusRecord entities
 */
@Repository
public interface StatusRecordRepository extends JpaRepository<StatusRecord, UUID> {

    /**
     * Find all status records for a job ordered by timestamp
     */
    @Query("SELECT sr FROM StatusRecord sr WHERE sr.jobId = :jobId ORDER BY sr.timestamp ASC")
    List<StatusRecord> findByJobIdOrderByTimestamp(@Param("jobId") UUID jobId);

    /**
     * Find the most recent status records for a job (for recent updates display)
     */
    @Query("SELECT sr FROM StatusRecord sr WHERE sr.jobId = :jobId ORDER BY sr.timestamp DESC")
    List<StatusRecord> findRecentByJobId(@Param("jobId") UUID jobId);

    /**
     * Find the latest status record for a job
     */
    @Query("SELECT sr FROM StatusRecord sr WHERE sr.jobId = :jobId ORDER BY sr.timestamp DESC LIMIT 1")
    StatusRecord findLatestByJobId(@Param("jobId") UUID jobId);
}
